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

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.widget.Toast;

public class SampleMenu extends Activity implements DynamicLayoutViewGroup.ItemSelectedListener {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);       
    
        // Find the DynamicLayoutViewGroup 
        DynamicLayoutViewGroup dlvg = (DynamicLayoutViewGroup)findViewById(R.id.menugroup);

        // Set ourself as a selection listener, useful if we are using
        // this as a fancy menu
        dlvg.setOnSelectionListener(this);
        
        // Set a new layout model.  To use the default LayoutModel, simply
        // comment out the following line        
        // dlvg.setLayoutModel(new SampleLayoutModel());
    }
    
    /**
     * This is a sample of how one might put together a layout model
     * for the DynamicLayoutViewGroup.  This arranges the unselected views
     * into rows of small views above and below the larger selected view
     * (or to the left and right if width>height.
     * 
     * Note that this example makes an assumption that there will be
     * 9 child views
     */
    public class SampleLayoutModel implements LayoutModel {
    	int viewheight;
    	int viewwidth;
    	
    	// which Rect will be used for which view
    	// the index is the position, the value is the view child #
    	int order[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
    	
    	// an array of Rect's for the views
    	Rect r[];
    	// an off screen Rect to use in case we need to return a
    	// Rect for a bad query
    	Rect badRect = new Rect( -10,-10, -9, -9);
    	
    	public SampleLayoutModel() {
    		// initialize the Rect's 
    		r = new Rect[9];    	
    		for (int i = 0;i<9;i++) {
    			r[i]=new Rect();
    		}
    	}
    	
		@Override
		public Rect getLayoutRect(int pos, int selected) {	
			// return the pre-established Rect that matches the 
			// position selected for this child.  
			Rect ret;
			
			// check for boundaries, this model can't manage
			// more than 9 views.  
			if ((pos < 9) && (selected < 9)) {
				if (selected != order[0]) {
					int s=0;
					while (order[s]!=selected) { s++; }
					// swap current selected with position of new
					// selected in layout
					order[s]=order[0];
					order[0]=selected;
				}
				
				int i=0;
				while (order[i]!=pos) { i++; }
				
				ret = r[i];
			} else {
				// if boundaries check fails, return an off screen rect
				ret = badRect;
			}
			return ret;
		}

		@Override
		public void onSizeChanged(int width, int height, int oldw, int oldh) {
			// This sample layout model creates all required rectangles
			// when the size of the view is set, then determines which
			// Rect to assign to each view when the layout rect is asked for
			viewheight=height;
			viewwidth=width;
			
			// for this layout model we will have a row of four small views
			// at the top and bottom, with the selected item large in the middle
			if (height > width) {
				int select_width = (int)(width * .80);
				int select_height = select_width;
				
				int select_top = (height-select_height)/2;
				int select_left = (width-select_width)/2;
				
				int unselect_height = (int)(select_top * .80);
				int unselect_width = unselect_height;
				int topbottom_margin = (select_top-unselect_height)/2;
				int spacing=5;
				
				if ((unselect_width*4) > (width-(5*spacing))) {
					// need to be narrower
					spacing = 5;
					unselect_width = (width-(5*spacing))/4;
					unselect_height = unselect_width;
				} else {
					// 4 views fit with at least spacing requested
					// determine exact spacing
					spacing = (width-(unselect_width*4))/5;
				}						
				
				r[0].set(select_left, select_top, select_left+select_width,select_top+select_height);
				for (int i=1;i<5;i++) {
					int left = spacing+((i-1)*(spacing+unselect_width));
					r[i].set(
							left,
							topbottom_margin,
							left+unselect_width,
							topbottom_margin+unselect_height);
				}
				for (int i=5;i<9;i++) {
					int left = spacing+((i-5)*(spacing+unselect_width));
					r[i].set(
							left,
							height-topbottom_margin-unselect_height,
							left+unselect_width,
							height-topbottom_margin);
				}
			} else {
				// if the view rotates so that the height is less than width
				// orient the non-selected views to the left and right				
				int select_height = (int)(height * .90);
				int select_width = select_height;
				
				int select_top = (height-select_height)/2;
				int select_left = (width-select_width)/2;
				
				int unselect_height = (int)(select_left * .80);
				int unselect_width = unselect_height;
				int leftright_margin = (select_left-unselect_width)/2;
				int spacing=5;
				
				if ((unselect_height*4) > (height-(5*spacing))) {
					// need to be narrower
					spacing = 5;
					unselect_height = (height-(5*spacing))/4;
					unselect_width = unselect_height;
				} else {
					// 4 views fit with at least spacing requested
					// determine exact spacing
					spacing = (height-(unselect_height*4))/5;
				}						
				
				r[0].set(select_left, select_top, select_left+select_width,select_top+select_height);
				for (int i=1;i<5;i++) {
					int top = spacing+((i-1)*(spacing+unselect_height));
					r[i].set(
							leftright_margin,
							top,							
							leftright_margin+unselect_width,
							top+unselect_height);
				}
				for (int i=5;i<9;i++) {
					int top = spacing+((i-5)*(spacing+unselect_height));
					r[i].set(
							width-leftright_margin-unselect_width,
							top,
							width-leftright_margin,
							top+unselect_height);
				}
			}
		}    	
    }

	@Override
	public void onItemSelected(int pos, int viewId) {
		Toast.makeText(this, "Pos "+pos+" viewId "+viewId+" selected", 3000).show();
	}
}