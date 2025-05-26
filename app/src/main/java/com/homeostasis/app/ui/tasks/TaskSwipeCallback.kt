package com.homeostasis.app.ui.tasks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R // Make sure this R is your app's R
import androidx.core.graphics.drawable.toDrawable

/**
 * Callback for handling swipe gestures and long press on task items.
 */
class TaskSwipeCallback(
    private val context: Context,
    private val onSwipeRight: (position: Int) -> Unit,
    private val onSwipeLeft: (position: Int) -> Unit,
    private val onEditClick: (position: Int) -> Unit = {},
    private val onDeleteClick: (position: Int) -> Unit = {}
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val completeBackground =
        ContextCompat.getColor(context, android.R.color.holo_green_light).toDrawable()
    private val undoBackground =
        ContextCompat.getColor(context, android.R.color.holo_red_light).toDrawable()
    // editDeleteBackground is no longer needed as the actions container has its own background
    private val clearPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }

    // editIcon and deleteIcon drawables are no longer needed here, they are in XML.

    private var currentlyShownItemView: View? = null
    private var currentlyShownPosition = RecyclerView.NO_POSITION
    private var isShowingActions = false

    // Threshold for sliding to reveal actions. This can be adjusted.
    // Could be a fixed DP value or a percentage of the item width.
    // For simplicity, let's use a fraction of the item width, e.g., 1/3.
    // You might want to calculate the actual width of the actions container
    // for a more precise slide.
    private val actionRevealSlideDistanceFactor = 0.33f // Reveals 1/3 of the view for actions

    // init block can be removed if not loading anything else, or kept if needed later.
    // init {
    // }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // We don't support moving items
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return

        // If actions are shown for this item, hide them before processing swipe
        if (isShowingActions && currentlyShownPosition == position) {
            hideActionsInstantly(viewHolder.itemView) // Hide instantly to avoid animation conflicts
        }

        when (direction) {
            ItemTouchHelper.RIGHT -> onSwipeRight(position)
            ItemTouchHelper.LEFT -> onSwipeLeft(position)
        }
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Disable swipe if actions are shown for the current item
        if (isShowingActions && viewHolder.adapterPosition == currentlyShownPosition) {
            return 0 // No swipe directions allowed
        }
        return super.getSwipeDirs(recyclerView, viewHolder)
    }


    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView

        // Do not draw swipe backgrounds if actions are shown for this item.
        // The item's content container will be translated, not the whole itemView for actions.
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isShowingActions && viewHolder.adapterPosition == currentlyShownPosition) {
            // If actions are shown, we just let the default draw handle the itemView's position
            // which should be static, while its child (task_content_container) is translated.
            // Or, ensure the dX is effectively 0 for the itemView itself if actions are shown.
            super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
            return
        }


        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            // Clear canvas only if not showing actions (where content is translated)
            if (! (isShowingActions && viewHolder.adapterPosition == currentlyShownPosition) ) {
                clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }


        // Draw background based on swipe direction (only if actions are NOT shown for this item)
        if (! (isShowingActions && viewHolder.adapterPosition == currentlyShownPosition) ) {
            when {
                dX > 0 -> { // Swiping right (complete)
                    completeBackground.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dX.toInt(),
                        itemView.bottom
                    )
                    completeBackground.draw(c)
                }
                dX < 0 -> { // Swiping left (undo)
                    undoBackground.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    undoBackground.draw(c)
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(left, top, right, bottom, clearPaint)
    }

    /**
     * Shows edit and delete actions for the given item view.
     */
    fun showActionsForItem(recyclerView: RecyclerView, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        // Hide actions for any previously shown item first
        if (isShowingActions && currentlyShownPosition != position && currentlyShownItemView != null) {
            hideActions(currentlyShownItemView!!) // Pass the specific view to hide
        } else if (isShowingActions && currentlyShownPosition == position) {
            // Already showing for this item, possibly do nothing or treat as a toggle
            hideActions(currentlyShownItemView!!)
            return
        }


        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) ?: return
        val itemView = viewHolder.itemView

        val taskContentContainer = itemView.findViewById<View>(R.id.task_content_container)
        val taskActionsContainer = itemView.findViewById<View>(R.id.task_actions_container) // For width calculation
        val editButton = itemView.findViewById<ImageView>(R.id.edit_icon)
        val deleteButton = itemView.findViewById<ImageView>(R.id.delete_icon)

        if (taskContentContainer == null || taskActionsContainer == null || editButton == null || deleteButton == null) {
            // Views not found, likely wrong layout or ID
            return
        }

        // Make sure actions container is visible before animation
        taskActionsContainer.visibility = View.VISIBLE

        editButton.setOnClickListener {
            onEditClick(position)
            hideActions(itemView) // Hide actions after click
        }

        deleteButton.setOnClickListener {
            onDeleteClick(position)
            hideActions(itemView) // Hide actions after click
        }

        // Calculate slide distance: e.g., width of the actions or a fraction of item width
        // For simplicity, using a fraction. For precision, measure taskActionsContainer or its children.
        val slideDistance = -(itemView.width * actionRevealSlideDistanceFactor)

        taskContentContainer.animate()
            .translationX(slideDistance)
            .setDuration(200)
            .start()

        currentlyShownItemView = itemView
        currentlyShownPosition = position
        isShowingActions = true

        // Optional: Request layout update for the recycler view if visuals are glitchy during animation
        // recyclerView.requestLayout()
    }

    /**
     * Hides the currently shown actions for a specific item view.
     */
    fun hideActions(itemViewToHide: View) {
        if (!isShowingActions || currentlyShownItemView != itemViewToHide) {
            // Not showing actions, or trying to hide a different item than the one currently shown
            if (itemViewToHide.findViewById<View>(R.id.task_content_container)?.translationX != 0f) {
                // If it's a different item but it's slid, reset it.
                itemViewToHide.findViewById<View>(R.id.task_content_container)?.animate()?.translationX(0f)?.setDuration(200)?.start()
            }
            if (currentlyShownItemView == itemViewToHide) { // Reset state if it was the one being hidden
                resetActionState()
            }
            return
        }

        val taskContentContainer = itemViewToHide.findViewById<View>(R.id.task_content_container)
        // val taskActionsContainer = itemViewToHide.findViewById<View>(R.id.task_actions_container) // Not strictly needed for hiding

        taskContentContainer?.animate()
            ?.translationX(0f)
            ?.setDuration(200)
            ?.withEndAction {
                // Optional: set taskActionsContainer.visibility = View.GONE if you want it completely gone
                // taskActionsContainer?.visibility = View.GONE

                // Reset state only if this was the item that was actively having its actions hidden
                if (currentlyShownItemView == itemViewToHide) {
                    resetActionState()
                }
            }
            ?.start()
    }

    /**
     * Hides actions for the currently shown item view without animation.
     * Useful when an item is about to be removed or swiped.
     */
    private fun hideActionsInstantly(itemViewToHide: View?) {
        if (itemViewToHide == null || !isShowingActions || currentlyShownItemView != itemViewToHide) {
            return
        }
        val taskContentContainer = itemViewToHide.findViewById<View>(R.id.task_content_container)
        taskContentContainer?.translationX = 0f
        // val taskActionsContainer = itemViewToHide.findViewById<View>(R.id.task_actions_container)
        // taskActionsContainer?.visibility = View.GONE // If you hide it on animation end
        resetActionState()
    }


    /**
     * Resets the state related to shown actions.
     */
    private fun resetActionState() {
        isShowingActions = false
        currentlyShownItemView = null
        currentlyShownPosition = RecyclerView.NO_POSITION
    }

    /**
     * Global hide actions if any item is showing them.
     * Typically called when user taps outside.
     */
    fun hideCurrentlyShownActions() {
        currentlyShownItemView?.let {
            hideActions(it)
        }
    }


    /**
     * Attaches a gesture detector to the recycler view to handle long press.
     */
    fun attachGestureDetector(recyclerView: RecyclerView) {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val childView = recyclerView.findChildViewUnder(e.x, e.y) ?: return
                val position = recyclerView.getChildAdapterPosition(childView)
                if (position != RecyclerView.NO_POSITION) {
                    showActionsForItem(recyclerView, position)
                }
            }

            // Optional: Handle single tap up to hide actions if they are shown
            // override fun onSingleTapUp(e: MotionEvent): Boolean {
            //     if (isShowingActions && currentlyShownItemView != null) {
            //         val rect = Rect()
            //         currentlyShownItemView!!.getGlobalVisibleRect(rect)
            //         // If tap is outside the currently shown item's bounds, hide actions
            //         // This is a simple check; more robust would be to check if the tap is on the item itself.
            //         if (!rect.contains(e.rawX.toInt(), e.rawY.toInt())) {
            //             hideCurrentlyShownActions()
            //             return true
            //         }
            //     }
            //     return false
            // }
        })

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // Let gesture detector process first
                val longPressConsumed = gestureDetector.onTouchEvent(e)
                if (longPressConsumed && e.action == MotionEvent.ACTION_DOWN) { // Long press initiated
                    rv.requestDisallowInterceptTouchEvent(true) // Prevent parent from scrolling during long press reveal
                }


                // If we're showing actions and the user taps, determine if it's inside the active item
                if (isShowingActions && e.action == MotionEvent.ACTION_DOWN) {
                    val viewUnderTap = rv.findChildViewUnder(e.x, e.y)
                    if (viewUnderTap == currentlyShownItemView) {
                        // Tapped outside the item that is currently showing actions
                        hideCurrentlyShownActions()
                        // Return false so the tap can be processed by other listeners if needed,
                        // unless hideCurrentlyShownActions should consume it.
                        return false
                    }
                    // If tap is on the item showing actions, let its children (edit/delete buttons) handle it.
                }
                return false // Do not intercept by default, let children or gesture detector handle
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // Gesture detector might also use this
                gestureDetector.onTouchEvent(e)
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // Not needed
            }
        })
    }
}