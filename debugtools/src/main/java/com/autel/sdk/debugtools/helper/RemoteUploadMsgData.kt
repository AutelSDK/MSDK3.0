package com.autel.sdk.debugtools.helper

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

/**
 * RC report messages
 * Messages are dispatched exactly once (no duplicates), also while in background, and are removed automatically per the LifecycleOwner lifecycle
 * Copyright: Autel Robotics
 *
 * @author Autel Robotics on 2022/11/25
 */
class RemoteUploadMsgData<T : Any> {

    private var value: T? = null

    private val mObservers: LinkedHashMap<Observer<T>, ObserverWrapper> = LinkedHashMap()


    /**
     * Observe the default drone data
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun observe(owner: LifecycleOwner, observer: Observer<T>) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        val wrapper = LifecycleBoundObserver(owner, observer)
        //putIfAbsent was added in API 24; runtimes below 24 do not have it.
        val existing =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mObservers.putIfAbsent(observer, wrapper)
            } else {
                var isExist = mObservers[observer]
                if (isExist == null) {
                    isExist = mObservers.put(observer, wrapper)
                }
                isExist
            }
        if (existing is LifecycleBoundObserver) {
            require(existing.isAttachedTo(owner)) {
                ("Cannot add the same observer"
                        + " with different lifecycles")
            }
        }
        if (existing != null) {
            return
        }
        owner.lifecycle.addObserver(wrapper)
    }

    /**
     * Observe data without a lifecycle
     */
    fun observeForever(observer: Observer<T>) {
        val wrapper = AlwaysActiveObserver(observer)
        val existing =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mObservers.putIfAbsent(observer, wrapper)
            } else {
                var isExist = mObservers[observer]
                if (isExist == null) {
                    isExist = mObservers.put(observer, wrapper)
                }
                isExist
            }
        require((existing is AlwaysActiveObserver)) {
            ("Cannot add the same observer"
                    + " with different lifecycles")
        }
    }

    /**
     * Report data
     */
    fun setValue(value: T) {
        this.value = value
        for (ob in mObservers.values) {
            ob.observer.onChanged(value)
        }
    }

    /**
     * Get data
     */
    fun getValue(): T? {
        return value
    }

    /**
     * Remove an observer
     */
    fun removeObserver(observer: Observer<T>) {
        val removed = mObservers.remove(observer) ?: return
        removed.detachObserver()
    }

    open inner class ObserverWrapper(val observer: Observer<T>) {
        open fun isAttachedTo(owner: LifecycleOwner): Boolean {
            return false
        }

        open fun detachObserver() {

        }
    }

    inner class LifecycleBoundObserver(private val mOwner: LifecycleOwner, observer: Observer<T>) :
        ObserverWrapper(observer), LifecycleEventObserver {

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            val currentState = mOwner.lifecycle.currentState
            if (currentState == Lifecycle.State.DESTROYED) {
                removeObserver(observer)
                return
            }
        }

        override fun isAttachedTo(owner: LifecycleOwner): Boolean {
            return mOwner === owner
        }

        override fun detachObserver() {
            mOwner.lifecycle.removeObserver(this)
        }
    }

    inner class AlwaysActiveObserver internal constructor(observer: Observer<T>) :
        ObserverWrapper(observer) {

    }
}