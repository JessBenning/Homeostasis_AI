package com.homeostasis.app.ui.tasks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R // Make sure this R is your app's R
import androidx.core.graphics.drawable.toDrawable

/**
 * Callback for handling swipe gestures AND managing the display of edit/delete actions
 * triggered by an options menu click.
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
    private val clearPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }

    private var currentlyShownItemView: View? = null
    private var currentlyShownPosition = RecyclerView.NO_POSITION
    private var isShowingActions = false
    private val actionRevealSlideDistanceFactor = 0.33f // Or calculate based on actual action view width

    // RecyclerView instance, needed to find ViewHolders if not passed directly
    private var attachedRecyclerView: RecyclerView? = null

    // Call this method when attaching to RecyclerView
    fun attachToRecyclerView(recyclerView: RecyclerView?) {
        attachedRecyclerView = recyclerView
        // If you still want the ItemTouchHelper for swipe, it will be attached separately.
        // The gesture detector for long press is removed from here.
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false // We don't support moving items
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return

        if (isShowingActions && currentlyShownPosition == position) {
            hideActionsInstantly(viewHolder.itemView)
        }

        when (direction) {
            ItemTouchHelper.RIGHT -> onSwipeRight(position)
            ItemTouchHelper.LEFT -> onSwipeLeft(position)
        }
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        if (isShowingActions && viewHolder.adapterPosition == currentlyShownPosition) {
            return 0 // No swipe directions allowed if actions for this item are shown
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

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isShowingActions && viewHolder.adapterPosition == currentlyShownPosition) {
            super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
            return
        }

        val isCanceled = dX == 0f && !isCurrentlyActive
        if (isCanceled) {
            if (!(isShowingActions && viewHolder.adapterPosition == currentlyShownPosition)) {
                clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        // Draw background and text for swipe actions
        if (!(isShowingActions && viewHolder.adapterPosition == currentlyShownPosition)) {
            val text: String
            val textColor: Int
            val intrinsicWidth: Int // Placeholder for icon width if icons were used

            when {
                dX > 0 -> { // Swiping right (Complete)
                    text = context.getString(R.string.swipe_completed) // Use string resource
                    textColor = ContextCompat.getColor(context, android.R.color.white) // White text
                    completeBackground.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    completeBackground.draw(c)

                    // Draw text
                    val textPaint = Paint().apply {
                        color = textColor
                        textSize = 48f // Adjust text size as needed
                        textAlign = Paint.Align.LEFT
                    }
                    val textX = itemView.left + 48 // Adjust padding as needed
                    val textY = itemView.top + (itemView.bottom - itemView.top) / 2f + textPaint.textSize / 2f
                    c.drawText(text, textX.toFloat(), textY.toFloat(), textPaint)
                }
                dX < 0 -> { // Swiping left (Undo)
                    text = context.getString(R.string.undo) // Use string resource
                    textColor = ContextCompat.getColor(context, android.R.color.white) // White text
                    undoBackground.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    undoBackground.draw(c)

                    // Draw text
                    val textPaint = Paint().apply {
                        color = textColor
                        textSize = 48f // Adjust text size as needed
                        textAlign = Paint.Align.RIGHT
                    }
                    val textX = itemView.right - 48 // Adjust padding as needed
                    val textY = itemView.top + (itemView.bottom - itemView.top) / 2f + textPaint.textSize / 2f
                    c.drawText(text, textX.toFloat(), textY.toFloat(), textPaint)
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(left, top, right, bottom, clearPaint)
    }

    /**
     * Toggles the display of edit and delete actions for the given item.
     * This will be called from the ViewHolder's options menu click.
     */
    fun toggleActionsForItem(position: Int) {
        val recyclerView = attachedRecyclerView ?: return
        if (position == RecyclerView.NO_POSITION) return

        if (isShowingActions && currentlyShownPosition == position) {
            // Already showing for this item, so hide it (toggle off)
            val vh = recyclerView.findViewHolderForAdapterPosition(position)
            vh?.itemView?.let { hideActions(it) }
        } else {
            // Hide actions for any previously shown item first
            if (isShowingActions && currentlyShownItemView != null) {
                hideActions(currentlyShownItemView!!)
            }
            // Now show for the new item
            showActionsInternal(recyclerView, position)
        }
    }


    private fun showActionsInternal(recyclerView: RecyclerView, position: Int) {
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) ?: return
        val itemView = viewHolder.itemView

        // Ensure IDs match your item layout: R.id.task_content_container for the part that slides,
        // and R.id.task_actions_container for the container of edit/delete buttons.
        val taskContentContainer = itemView.findViewById<View>(R.id.task_content_container)
        val taskActionsContainer = itemView.findViewById<View>(R.id.task_actions_container)
        val editButton = itemView.findViewById<ImageView>(R.id.edit_icon)
        val deleteButton = itemView.findViewById<ImageView>(R.id.delete_icon)

        if (taskContentContainer == null || taskActionsContainer == null || editButton == null || deleteButton == null) {
            // Log.e("TaskSwipeCallback", "Required views not found in item layout for actions.")
            return
        }

        taskActionsContainer.visibility = View.VISIBLE

        taskActionsContainer.post {
            val actionsContainerActualWidth = taskActionsContainer.width
            val itemViewWidth = itemView.width

            android.util.Log.d("TaskSwipeCallback", "Position: $position")
            android.util.Log.d("TaskSwipeCallback", "itemView.width: $itemViewWidth")
            android.util.Log.d("TaskSwipeCallback", "taskActionsContainer.width (after post): $actionsContainerActualWidth")
            android.util.Log.d("TaskSwipeCallback", "actionRevealSlideDistanceFactor: $actionRevealSlideDistanceFactor")

            val calculatedSlideWidth: Float = if (actionsContainerActualWidth > 0) {
                actionsContainerActualWidth.toFloat()
            } else {
                android.util.Log.w("TaskSwipeCallback", "taskActionsContainer.width is 0 or less. Using fallback.")
                (itemViewWidth * actionRevealSlideDistanceFactor)
            }
            android.util.Log.d("TaskSwipeCallback", "Calculated Slide Width for animation: $calculatedSlideWidth")

            val slideDistance = -calculatedSlideWidth

            taskContentContainer.animate()
                .translationX(slideDistance)
                .setDuration(200)
                .start()

            // Set these after the animation starts or configuration is done
            currentlyShownItemView = itemView
            currentlyShownPosition = position
            isShowingActions = true
        }

        editButton.setOnClickListener {
            onEditClick(position)
            hideActions(itemView)
        }

        deleteButton.setOnClickListener {
            onDeleteClick(position)
            hideActions(itemView)
        }

        // Inside TaskSwipeCallback.kt, in the showActionsInternal method:

        // Calculate slide distance based on the width of the actions container or a fixed factor
        // Ensure this calculation results in a Float if any part is a Float.
        // itemView.width is Int, actionRevealSlideDistanceFactor is Float.
        // taskActionsContainer.width is Int.
//        val calculatedSlideWidth: Float = taskActionsContainer.width.takeIf { it > 0 }?.toFloat()
//            ?: (itemView.width * actionRevealSlideDistanceFactor)
//
//        // Now, apply negation to the Float variable
//        val slideDistance = -calculatedSlideWidth
//
//        taskContentContainer.animate()
//            .translationX(slideDistance)
//            .setDuration(200)
//            .start()
//
//        currentlyShownItemView = itemView
//        currentlyShownPosition = position
//        isShowingActions = true
    }

    fun hideActions(itemViewToHide: View) {
        if (!isShowingActions || currentlyShownItemView != itemViewToHide) {
            // Not showing actions for this specific view, or trying to hide a different one
            // If it's a different view but it IS translated, reset it.
            val contentContainer = itemViewToHide.findViewById<View>(R.id.task_content_container)
            if (contentContainer?.translationX != 0f) {
                contentContainer?.animate()?.translationX(0f)?.setDuration(200)?.start()
            }
            // If this was the item being hidden, ensure state is fully reset
            if (currentlyShownItemView == itemViewToHide) {
                resetActionState()
                itemViewToHide.findViewById<View>(R.id.task_actions_container)?.visibility = View.GONE
            }
            return
        }

        val taskContentContainer = itemViewToHide.findViewById<View>(R.id.task_content_container)
        val taskActionsContainer = itemViewToHide.findViewById<View>(R.id.task_actions_container)

        taskContentContainer?.animate()
            ?.translationX(0f)
            ?.setDuration(200)
            ?.withEndAction {
                taskActionsContainer?.visibility = View.GONE // Hide after animation
                if (currentlyShownItemView == itemViewToHide) { // Double check
                    resetActionState()
                }
            }
            ?.start()
    }

    private fun hideActionsInstantly(itemViewToHide: View?) {
        if (itemViewToHide == null || !isShowingActions || currentlyShownItemView != itemViewToHide) {
            return
        }
        itemViewToHide.findViewById<View>(R.id.task_content_container)?.translationX = 0f
        itemViewToHide.findViewById<View>(R.id.task_actions_container)?.visibility = View.GONE
        resetActionState()
    }

    private fun resetActionState() {
        isShowingActions = false
        currentlyShownItemView = null
        currentlyShownPosition = RecyclerView.NO_POSITION
    }

    fun hideCurrentlyShownActions() {
        if (isShowingActions && currentlyShownItemView != null) {
            hideActions(currentlyShownItemView!!)
        }
    }

    // REMOVE the attachGestureDetector method and its RecyclerView.OnItemTouchListener
    // fun attachGestureDetector(recyclerView: RecyclerView) { ... }
}