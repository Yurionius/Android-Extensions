package com.tunjid.androidx.navigation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellableContinuation

class SuspendingMultiStackNavigator(
    private val navigator: MultiStackNavigator
) : SuspendingNavigator by CommonSuspendingNavigator(navigator) {

    suspend fun show(index: Int) = mainThreadSuspendCancellableCoroutine<Fragment?> { continuation ->
        navigator.stackFragments[navigator.activeIndex].doOnLifecycleEvent(Lifecycle.Event.ON_RESUME) {
            when (val upcomingStack = navigator.stackFragments.getOrNull(index)) {
                null -> continuation.resumeIfActive(null)  // out of index. Throw an exception maybe?
                else -> upcomingStack.waitForChild(continuation)
            }
            navigator.show(index)
        }
    }

    override suspend fun clear(upToTag: String?, includeMatch: Boolean) =
        SuspendingStackNavigator(navigator.activeNavigator).clear(upToTag, includeMatch)

    /**
     * @see MultiStackNavigator.clearAll
     */
    suspend fun clearAll() {
        internalClearAll()
    }

    private suspend fun internalClearAll(): Fragment? = mainThreadSuspendCancellableCoroutine { continuation ->
        navigator.activeFragment.doOnLifecycleEvent(Lifecycle.Event.ON_RESUME) {
            navigator.reset(commitNow = false) {
                navigator.stackFragments[0].waitForChild(continuation)
            }
        }
    }
}

private fun StackFragment.waitForChild(continuation: CancellableContinuation<Fragment?>) = doOnLifecycleEvent(Lifecycle.Event.ON_RESUME) {
    when (val current = navigator.current) {
        null -> { // Root has not been shown yet, defer until the first fragment shows
            val fragmentManager = navigator.fragmentManager
            fragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    continuation.resumeIfActive(f)
                    fragmentManager.unregisterFragmentLifecycleCallbacks(this)
                }
            }, false)
        }
        else -> continuation.resumeIfActive(current)
    }
}