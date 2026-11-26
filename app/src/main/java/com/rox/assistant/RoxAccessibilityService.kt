package com.rox.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RoxAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RoxAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen empty"
        val pkg = root.packageName?.toString() ?: ""
        if (pkg.contains("phonepe") || pkg.contains("paisa") || pkg.contains("paytm") || pkg.contains("bank")) {
            return "Blocked on payment or banking app for safety."
        }
        val sb = StringBuilder()
        collectText(root, sb)
        return sb.toString().ifEmpty { "No readable text found" }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (!node.isPassword) {
            node.text?.let { if (it.isNotBlank()) sb.append(it).append("\n") }
            node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append("\n") }
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb)
        }
    }

    fun clickByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val list = root.findAccessibilityNodeInfosByText(query)
        for (node in list) {
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable) {
                    return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                target = target.parent
            }
        }
        return false
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollScreen(direction: String): Boolean {
        val display = resources.displayMetrics
        val midX = display.widthPixels / 2f
        val startY = if (direction == "down") display.heightPixels * 0.75f else display.heightPixels * 0.25f
        val endY = if (direction == "down") display.heightPixels * 0.25f else display.heightPixels * 0.75f

        val path = Path().apply {
            moveTo(midX, startY)
            lineTo(midX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun performSystemKey(key: String): Boolean {
        val action = when (key.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        return performGlobalAction(action)
    }
}
