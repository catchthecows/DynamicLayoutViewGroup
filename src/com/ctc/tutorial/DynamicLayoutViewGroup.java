/*
 * Copyright (C) 2011 Scott Lund
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.tutorial;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.view.animation.Interpolator;
import java.util.HashMap;

public final class DynamicLayoutViewGroup extends ViewGroup {

	/**
	 *  Velocity tracking for fling detection in touch events
	 */
	private VelocityTracker mVelocityTracker;

	private int mTouchSlop;
	private int mMinimumVelocity;
	private int mMaximumVelocity;

	private static final int INVALID_POINTER = -1;

	private int mActivePointerId = INVALID_POINTER;

	private float mLastMotionX;
	private float mLastMotionY;

	private boolean mIsBeingDragged = false;

	/**
	 * Background drawing support
	 */
	private RectF fullRect;
	private Paint fullPaint;

	/**
	 * Variables used in controlling the animation
	 */
	long mLastDrawTime = -1;
	long mStartDrawTime = -1;

	int mAnimationDuration = 500;

	boolean mAnimating = false;
	Interpolator mAnimationInterpolator = null;

	HashMap<View, ViewHelper> helperList = new HashMap<View, ViewHelper>();

	ItemSelectedListener mSelectionListener=null;
	/**
	 * Hang on to the context 
	 */
	Context _context;
	
	/**
	 * The Layout model used to position the views.
	 *   the default model is a set of horizontal squares
	 *   with the selected item centered and a bit larger
	 */
	LayoutModel mLayoutModel = new DefaultLayoutModel();
	
	/**
	 *  The index of the current "selected" item
	 */ 	
	private int _selected = 0;


	/**
	 * Constructors
	 * @param context
	 */
	public DynamicLayoutViewGroup(Context context) {
		super(context);
		init(context);
	}

	public DynamicLayoutViewGroup(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public DynamicLayoutViewGroup(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}
	
	/**
	 * set the layout model for this view group
	 * @param model
	 */
	public void setLayoutModel( LayoutModel model ) {
		mLayoutModel = model;
		// layoutChildren();
	}
	
	/**
	 * set the animation interpolator to use when animating
	 * view layout changes.  the default is OvershootInterpolator
	 * for that nice settling in wiggle
	 * @param interpolator
	 */
	public void setInterpolator( Interpolator interpolator ) {
		mAnimationInterpolator = interpolator;
	}
	
	/**
	 * add a listener for selection events.  The listener gets called
	 * when the selected view is tapped
	 */
	public void setOnSelectionListener( ItemSelectedListener listener ) {
		mSelectionListener = listener;
	}

	/**
	 * restore the position of the selected item  
	 */
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		Parcelable superState = bundle.getParcelable("superState");
		super.onRestoreInstanceState(superState);

		_selected = bundle.getInt("selected");
	}
	
	/**
	 * Save the position of the selected item so that it will
	 * restore on rotate 
	 */
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable("superState", superState);
		state.putInt("selected", _selected); 
		return state;
	}

	/**
	 * Setup for the view. Grab some configuration values for 
	 * touch events and set the default interpolator.
	 * @param context
	 */
	private void init(Context context) {
		_context = context;
		initDrawingTools();

		final ViewConfiguration configuration = ViewConfiguration
				.get(getContext());
		mTouchSlop = configuration.getScaledTouchSlop();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

		mAnimationInterpolator = new OvershootInterpolator();
	}

	/**
	 * setup drawing any drawing tools required to paint the
	 * background
	 */
	private void initDrawingTools() {
		fullRect = new RectF(0.0f, 0.05f, 1.0f, .95f);

		LinearGradient fullShader = new LinearGradient(0f, 0f, 0f, 0.9f,
				0xFF000000, 0xFF4f4f4f, Shader.TileMode.MIRROR);
		fullPaint = new Paint();
		fullPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		fullPaint.setShader(fullShader);
	}

	/**
	 * Layout the children
	 */
	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		layoutChildren();
	}


	
	/**
	 * To lay out the children, call the installed LayoutModel for
	 * each view.  The model will return a Rect for positioning of
	 * the view.  
	 * 
	 * Here we also handle animation between layout positions
	 */
	protected void layoutChildren() {
		int i;
		
		Rect r;

		// Is the view animating to new layout positions?
		if (mAnimating) {
			if (mStartDrawTime == -1) {
				mStartDrawTime = SystemClock.uptimeMillis();
			}
			// here we are moving the views and forcing layout
			// to update the child view locations
			// get current time
			long timenow = SystemClock.uptimeMillis();
			// if animation time not expired
			if ((timenow - mStartDrawTime) < mAnimationDuration) {
				// determine time as %
				float tdiff = (float) timenow - (float) mStartDrawTime;
				float timeslice = mStartDrawTime == -1 ? .05f : tdiff
						/ (float) mAnimationDuration;
				// use layout helper to layout views
				// the layoutAtTime method will determine where to
				// position the view based on % between the start position
				// and requested end position.  timeslice is a % value from
				// 0 to 1 depending on how much animation time remains.  
				// the view helper is also where the interpolator is
				// called.
				for (ViewHelper vh : helperList.values()) {
					vh.layoutAtTime(timeslice);
				}
				mLastDrawTime = SystemClock.uptimeMillis();
				// post event to do the layout again
				post(new Runnable() {
					public void run() {
						layoutChildren();
					}
				});
			} else {
				// the animation duration is passed
				// set mAnimation false
				mAnimating = false;
				mLastDrawTime = -1;
				mStartDrawTime = -1;
				// and call layoutChildren to set final locations using
				// non animation branch of layout
				layoutChildren();
			}
		} else {
			// when no animation is happening, we simply 
			// Layout children based on the values provided by
			// the LayoutModel
			View v;
			ViewHelper vh;
			
			for (i = 0; i < getChildCount(); i++) {
				v = getChildAt(i);
				r = mLayoutModel.getLayoutRect(i,_selected);
				v.layout(r.left, r.top, r.right,r.bottom);
				// also, store this position as the start position
				// for the next time we want to animate
				vh = getViewHelper(v, i);				
				vh.setStartPosition(r.left, r.top, r.right,r.bottom);				
			}
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		if (widthMode != MeasureSpec.EXACTLY) {
			throw new IllegalStateException(
					"This ViewGroup can only be used in EXACTLY mode.");
		}

		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if (heightMode != MeasureSpec.EXACTLY) {
			throw new IllegalStateException(
					"This ViewGroup can only be used in EXACTLY mode.");
		}
		
		// Measure out the children 
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View v = getChildAt(i);
			// use the layout model to determine the size and height
			Rect r = mLayoutModel.getLayoutRect(i, _selected);
			final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(r.right-r.left,
					MeasureSpec.EXACTLY);
			final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(r.bottom-r.top,
					MeasureSpec.EXACTLY);

			v.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}

	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		// Here is where you would draw custom background bits
		// for the demo I just draw a shaded gradient unless there
		// is a drawable set as the background in the XML file
		// An improvement might be to ask the LayoutModel for info
		// about the background (ie, x,y position in a drawable) 
		// to use which would allow for nice background scrolling effect
		if (getBackground() == null) {
			float width = (float) getWidth();
			float height = (float) getHeight();
			
			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.scale(width, height); canvas.drawRect(fullRect, fullPaint);  
			canvas.restore();
		}
		 
		// once the background is drawn then dispatch to children
		final long drawingTime = getDrawingTime();
		// for now we just draw all of our children
		// Could be optimized to draw only visible children
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			drawChild(canvas, getChildAt(i), drawingTime);
		}
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldw, int oldh) {
		// notify the layout model that the size of the parent
		// has changed.
		mLayoutModel.onSizeChanged(width, height, oldw, oldh);
	}

	/**
	 * Handle touch events for the view group, determining 
	 * scrolling and flinging as appropriate
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
			// Don't handle edge touches immediately -- they may actually belong
			// to one of our
			// descendants.
			return false;
		}

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);

		final int action = ev.getAction();

		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN: {
			final float x = ev.getX();
			final float y = ev.getY();

			// Remember where the motion event started
			mLastMotionX = x;
			mLastMotionY = y;
			mActivePointerId = ev.getPointerId(0);
			break;
		}
		case MotionEvent.ACTION_MOVE:
		
			// Scroll to follow the motion event
			final int activePointerIndex = ev
					.findPointerIndex(mActivePointerId);
	
			final float x = ev.getX(activePointerIndex);
			final int xDiff = (int) Math.abs(mLastMotionX - x);
			final float y = ev.getY(activePointerIndex);
			final int yDiff = (int) Math.abs(mLastMotionY - y);

			// only start a drag if enough motion has occurred
			if (!mIsBeingDragged) {
				if ((xDiff > mTouchSlop) || (yDiff > mTouchSlop)) {
					mIsBeingDragged = true;
				}
			} else {
				// This demo version does not attempt to scroll to
				// track the active pointer.  Hint.  This is where you
				// would implement that functionality if you so desired.
			    mLastMotionX = x;
			}
			
			break;
		case MotionEvent.ACTION_UP:
			if (mIsBeingDragged) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				
				// for now we are allowing a vertical or horizontal
				// fling to cause action.  One could imagine adding
				// a setting (possible from the LayoutModel)
				// to restrict this to horizontal
				// or vertical depending on need
				int xVelocity = (int) velocityTracker.getXVelocity();
				int yVelocity = (int) velocityTracker.getYVelocity();

				if (Math.abs(xVelocity) > Math.abs(yVelocity)) {
					// horizontal fling
					if ((Math.abs(xVelocity) > mMinimumVelocity)) {
						flingX(-xVelocity);
					}
				} else {
					// vertical fling
					if ((Math.abs(yVelocity) > mMinimumVelocity)) {
						flingY(-yVelocity);
					}
				}

				mActivePointerId = INVALID_POINTER;
				mIsBeingDragged = false;

				if (mVelocityTracker != null) {
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}

			} else {
				// this was a non-drag tap and release
				// use this to determine which entry was tapped
				// and make it the selected view or do an action
				// if the selected view is being tapped
				findAndSelectViewAt((int) mLastMotionX, (int) mLastMotionY);
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			if (mIsBeingDragged) {
				mActivePointerId = INVALID_POINTER;
				mIsBeingDragged = false;
				if (mVelocityTracker != null) {
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}
			}
			break;
		case MotionEvent.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			break;
		}
		return true;
	}

	/**
	 * "borrowed" this code direct from the default workspace Launcher2 code.
	 * It attempts to make the swipe action work when the there are 
	 * multiple touch points and the one we were tracking goes away
	 * @param ev
	 */
	private void onSecondaryPointerUp(MotionEvent ev) {
		final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;

		final int pointerId = ev.getPointerId(pointerIndex);
		if (pointerId == mActivePointerId) {
			// This was our active pointer going up. Choose a new
			// active pointer and adjust accordingly.
			// TODO: Make this decision more intelligent.
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mLastMotionX = ev.getX(newPointerIndex);
			mActivePointerId = ev.getPointerId(newPointerIndex);
			if (mVelocityTracker != null) {
				mVelocityTracker.clear();
			}
		}
	}

	/**
	 * Fling the scroll view
	 * 
	 * @param velocityX
	 *            The initial velocity in the X direction. Positive numbers mean
	 *            that the finger/cursor is moving down the screen, which means
	 *            we want to scroll towards the left.
	 */
	public void flingX(int velocityX) {
		Log.d("CTC", "flingX v:" + velocityX);
		if (velocityX > 0) {
			next();
		} else {
			prev();
		}
	}

	public void flingY(int velocityY) {
		// for now, fling vertical will just move the list by one view
		Log.d("CTC", "flingY v:" + velocityY);
		if (velocityY > 0) {
			next();
		} else {
			prev();
		}
	}

	protected void findAndSelectViewAt(int x, int y) {
		// Note: this is very inefficient and does not manage overlaps
		// we simple iterate the views and find the first one that
		// the point tapped lands in.
		for (ViewHelper vh : helperList.values()) {
			if (vh.isPointInView(x, y)) {
				int i = vh.getIndex();
				if (i != -1) {
					if (i == _selected) {
						// the current selected view has been tapped
						if (mSelectionListener != null) {
							mSelectionListener.onItemSelected(i, vh.getView().getId());
						}
					} else {
						moveTo(i);
					}
				}
			}
		}
	}

	
	protected void selectChild(int newselection) {
		// set new selection index and re-measure children for new
		// position
		// TODO: It would be better if we could re-measure
		//       at each step of the animation
		//       when just animating ImageViews around everything looks fine
		//       but if you animate more complex content a smooth change
		//       would make a better presentation.  Exercise for the future.
		_selected = newselection;
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {

			View v = getChildAt(i);
			Rect r = mLayoutModel.getLayoutRect(i, _selected);
			final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(r.right-r.left,
					MeasureSpec.EXACTLY);
			final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(r.bottom-r.top,
					MeasureSpec.EXACTLY);

			v.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	protected void animateLayout() {
		int i;

		// set up ViewHelpers to animate the views to a new location
		// by asking for new layout Rect and passing in the updated
		// selected item position.  The current position is already
		// stored away
		View v;
		ViewHelper vh;
		Rect r;
		for (i = 0; i < getChildCount(); i++) {
			v = getChildAt(i);
			vh = getViewHelper(v,i);
			r = mLayoutModel.getLayoutRect(i, _selected);
			vh.setTargetPosition(r.left,r.top,r.right,r.bottom);
		}

		// once all the target positions are gathered
		// set the "please animate me" flag and call layout
		mAnimating = true;
		mStartDrawTime = -1;
		layoutChildren();
	}

	/**
	 * moveUp and next are the same action
	 * moveUp() is internal and does not check boundaries
	 * next() is public and does
	 */
	protected void moveUp() {
		if (!mAnimating) {
			moveTo(_selected + 1);
		}
	}
	
	public int next() {
		if (_selected < getChildCount() - 1) {
			moveUp();
		}

		return _selected;
	}

	/**
	 * moveDown and prev are the same action
	 * moveDown() is internal and does not check boundaries
	 * prev() is public and does
	 */
	protected void moveDown() {
		if (!mAnimating) {
			moveTo(_selected - 1);
		}
	}
	
	public int prev() {
		if (_selected > 0) {
			moveDown();
		}
		return _selected;
	}
	
	/**
	 * Attempt to set the selected child and animate
	 * If animation is happening or the target child
	 * is invalid, do nothing
	 * @param index
	 */
	protected void moveTo(int index) {
		if (!mAnimating) {
			if ((index != _selected) && (index >= 0)
					&& (index < getChildCount())) {
				selectChild(index);
				
				animateLayout();			
			}
		}
	}

	/**
	 * setSelection is a nice public entry point for moveTo
	 * @param position
	 */
	public void setSelection(int position) {
		moveTo(position);
	}

	/**
	 * public access to the selected child index
	 * @return
	 */
	public int getSelection() {
		return _selected;
	}
	
	
	/**
	 * ViewHelper is a wrapper on the data required to animate a view
	 * from a start position to a target position.
	 */
	private class ViewHelper {
		// The start left top right bottom
		int s_l;
		int s_t;
		int s_r;
		int s_b;
		// the target (or end) left top right bottom
		int e_l;
		int e_t;
		int e_r;
		int e_b;
		int index;
		View _v;

		ViewHelper(View v, int i) {
			_v = v;
			setIndex(i);
		}

		void setStartPosition(int l, int t, int r, int b) {
			s_l = l;
			s_t = t;
			s_r = r;
			s_b = b;
		}

		void setTargetPosition(int l, int t, int r, int b) {
			e_l = l;
			e_t = t;
			e_r = r;
			e_b = b;
		}

		void setIndex(int i) {
			index = i;
		}

		int getIndex() {
			return index;
		}
		
		View getView() {
			return _v;
		}

		/**
		 * layoutAtTime is called with a value that represents the 
		 * current time between the start of the animation and when it
		 * should end as a percentage (value from 0 to 1.0)
		 * 
		 * Using this value we simply determine where we are on a straight line
		 * between the start and end point 
		 * 
		 * Just like any of the Animation classes, we can also have an
		 * Interpolator set which provides an effect by tweaking the timeslice
		 * value.
		 * 
		 * Once the current position is calculated, call layout on the view
		 * 
		 * @param t_req  the current timeslice %
		 */
		void layoutAtTime(float t_req) {
			float t;

			if (mAnimationInterpolator != null) {
				t = mAnimationInterpolator.getInterpolation(t_req);
			} else {
				t = t_req;
			}

			int d_l = (int) ((e_l - s_l) * t);
			int cur_l = s_l + d_l;
			int d_t = (int) ((e_t - s_t) * t);
			int cur_t = s_t + d_t;
			int d_r = (int) ((e_r - s_r) * t);
			int cur_r = s_r + d_r;
			int d_b = (int) ((e_b - s_b) * t);
			int cur_b = s_b + d_b;

			_v.layout(cur_l, cur_t, cur_r, cur_b);
		}

		/**
		 * For touch events, determine if the x,y coordinate provided
		 * falls within this view.
		 * @param x
		 * @param y
		 * @return true if x,y is in the view bounds
		 *         false otherwise
		 */
		boolean isPointInView(int x, int y) {
			return ((index != -1) && (s_l < x) && (s_r > x) && (s_t < y) && (s_b > y));
		}
	}
	
	/**
	 * Find the view helper for a view or create one if none exists
	 * @param v
	 * @param pos
	 * @return
	 */
	protected ViewHelper getViewHelper(View v, int pos) {
		ViewHelper vh = helperList.get(v);
		if (vh == null) {
			vh = new ViewHelper(v, pos);
			helperList.put(v, vh);
		}
		return vh;
	}
	
	/**
	 * The child view layout is managed with a LayoutModel
	 * This view group is mainly designed to handle selection lists where
	 * the positioning of the views is based off of what child view
	 * is currently selected.  Things like short lists where the selected item
	 * becomes centered larger or a horizontal scrolling menu of images.
	 * 
	 * As more uses come to mind one could add callbacks to the model to set
	 * important information or retrieve states
	 * 
	 * This implementation of the LayoutModel provides a default
	 * where each view is a square with the selected view centered in a 
	 * horizontal line of views.  
	 * 
	 * To use this layout model as is, simply extend this model and 
	 * override the selection methods
	 */
	public class DefaultLayoutModel implements LayoutModel {
		int viewspacing = 20;
		
		int layoutHeight=-1;
		int layoutWidth=-1;
		
		int focus_height;
		int focus_width;
		int unfocus_height;
		int unfocus_width;
		
		/**
		 * called when the parent view size changes
		 */
		public void onSizeChanged(int width, int height, int oldw, int oldh) {
			layoutHeight = height;
			layoutWidth = width;
			
			// try to get the child views to measure as follows
			if (height > width) {
				focus_height = (int) (height * .30);			
				unfocus_height = focus_height - (viewspacing *2);
				focus_width = focus_height;
				unfocus_width = unfocus_height;
			} else {
				focus_height = (int) (height * .60);			
				unfocus_height = focus_height - (viewspacing *2);
				focus_width = focus_height;
				unfocus_width = unfocus_height;
			}
		}
		
		/**
		 * called for each child (by index) to get a layout Rect
		 */
		public Rect getLayoutRect(int pos, int selected) {
			Rect rec = new Rect();

			int s_top = layoutHeight / 2 - focus_height / 2;
			int s_left = layoutWidth / 2 - focus_width / 2;

			int t;
			int l;
			int b;
			int r;
			
			if (pos < selected) {
				l = s_left - ((unfocus_width+viewspacing)*(selected-pos));
				t = s_top + viewspacing;
				r = l + unfocus_width;
				b = t + unfocus_height;
			} else if (pos == selected) {
				// pos is the selected item
				l = s_left;
				t = s_top;
				r = s_left + focus_width;
				b = s_top + focus_height;
			} else {
				// pos > selected
				l = s_left + focus_width + viewspacing + ((unfocus_width+viewspacing)*(pos-selected-1));
				t = s_top + viewspacing;
				r = l + unfocus_width;
				b = t + unfocus_height;		
			}
			
			rec.set(l,t,r,b);
			
			return rec;		
		}
	}
	
	public interface ItemSelectedListener {
		public void onItemSelected(int pos, int viewId);
	}
		
}
