package com.homeostasis.app.ui.tasks

import android.content.Context
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R

/**
 * Touch listener for handling long press on task items to reveal edit and delete actions.
 */
class TaskItemTouchListener(
    context: Context,
    private val recyclerView: RecyclerView,
    private val onEditClick: (position: Int) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val gestureDetector: GestureDetector
    private var longPressedView: View? = null
    private var backgroundView: View? = null
    private var longPressedPosition = RecyclerView.NO_POSITION

    init {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                try {
                    // Find the view that was long-pressed
                    val childView = recyclerView.findChildViewUnder(e.x, e.y) ?: return
                    
                    // Get the position of the item
                    longPressedPosition = recyclerView.getChildAdapterPosition(childView)
                    if (longPressedPosition != RecyclerView.NO_POSITION) {
                        // Make sure the ViewHolder exists
                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(longPressedPosition)
                        if (viewHolder != null) {
                            longPressedView = childView
                            showActionIcons(childView)
                        }
                    }
                } catch (e: Exception) {
                    // Log the error but don't crash
                    e.printStackTrace()
                }
            }
        })
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(e)
        
        // If we have a long-pressed view and the user lifts their finger, hide the action icons
//        if (longPressedView != null && e.actionMasked == MotionEvent.ACTION_UP) {
//            hideActionIcons()
//            return true
//        }
        
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        try {
            // If we have a long-pressed view and the user is moving their finger,
            // check if they're touching one of the action icons
            if (longPressedView != null && backgroundView != null) {
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    val editIcon = backgroundView!!.findViewById<ImageView>(R.id.edit_icon)
                    val deleteIcon = backgroundView!!.findViewById<ImageView>(R.id.delete_icon)
                    
                    if (editIcon != null && deleteIcon != null) {
                        // Check if the touch is on the edit icon
                        if (isTouchingView(editIcon, e.rawX, e.rawY)) {
                            onEditClick(longPressedPosition)
                        }
                        
                        // Check if the touch is on the delete icon
                        else if (isTouchingView(deleteIcon, e.rawX, e.rawY)) {
                            onDeleteClick(longPressedPosition)
                        }
                    }
                    
                    hideActionIcons()
                }
            }
        } catch (ex: Exception) {
            // Log the error but don't crash
            ex.printStackTrace()
            hideActionIcons()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Not needed for this implementation
    }

    private fun showActionIcons(itemView: View) {
        try {
            // Create the background view with action icons
            val parent = itemView.parent as? ViewGroup ?: return
            backgroundView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.item_task_background, parent, false)
            
            // Position the background view behind the item view
            val layoutParams = ViewGroup.LayoutParams(
                itemView.width,
                itemView.height
            )
            backgroundView!!.layoutParams = layoutParams
            
            // Add the background view to the parent
            val index = parent.indexOfChild(itemView)
            if (index >= 0) {
                parent.addView(backgroundView, index)
                
                // Slide the item view to reveal the background
                itemView.animate()
                    .translationX(-(itemView.width / 3).toFloat())
                    .setDuration(200)
                    .start()
            } else {
                backgroundView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            backgroundView = null
        }
    }

    private fun hideActionIcons() {
        try {
            if (longPressedView != null && backgroundView != null) {
                // Slide the item view back to its original position
                longPressedView!!.animate()
                    .translationX(0f)
                    .setDuration(200)
                    .withEndAction {
                        try {
                            // Remove the background view
                            val parent = longPressedView?.parent as? ViewGroup
                            if (parent != null && backgroundView != null) {
                                parent.removeView(backgroundView)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            // Reset state
                            longPressedView = null
                            backgroundView = null
                            longPressedPosition = RecyclerView.NO_POSITION
                        }
                    }
                    .start()
            } else {
                // Reset state if views are null
                longPressedView = null
                backgroundView = null
                longPressedPosition = RecyclerView.NO_POSITION
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Reset state
            longPressedView = null
            backgroundView = null
            longPressedPosition = RecyclerView.NO_POSITION
        }
    }

    private fun isTouchingView(view: View?, x: Float, y: Float): Boolean {
        if (view == null) return false
        
        try {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            
            val left = location[0]
            val top = location[1]
            val right = left + view.width
            val bottom = top + view.height
            
            return x >= left && x <= right && y >= top && y <= bottom
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}