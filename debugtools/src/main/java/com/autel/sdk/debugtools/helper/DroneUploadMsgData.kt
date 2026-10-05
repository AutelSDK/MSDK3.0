package com.autel.sdk.debugtools.helper

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import kotlin.collections.set

/**
 * Drone report message wrapper
 * Messages are dispatched exactly once (no duplicates), also while in background, and are removed automatically per the LifecycleOwner lifecycle
 * Copyright: Autel Robotics
 *
 * @author Autel Robotics on 2022/11/23
 */
class DroneUploadMsgData<T> {

    private val map: HashMap<IBaseDevice, T> = HashMap()

    private val mObservers: LinkedHashMap<Observer<T>, ObserverWrapper> = LinkedHashMap()

    private val mAutelDroneObservers: LinkedHashMap<Observer<Pair<IBaseDevice, T>>, ObserverAutelDroneWrapper> =
        LinkedHashMap()

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
     * Observe all drone data
     */

    @RequiresApi(Build.VERSION_CODES.N)
    fun observeAutelDrone(owner: LifecycleOwner, observer: Observer<Pair<IBaseDevice, T>>) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        val wrapper = LifecycleBoundAutelDroneObserver(owner, observer)
        val existing = mAutelDroneObservers.putIfAbsent(observer, wrapper)
        if (existing is LifecycleBoundAutelDroneObserver) {
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
    @RequiresApi(Build.VERSION_CODES.N)
    fun observeForever(observer: Observer<T>) {
        val wrapper = AlwaysActiveObserver(observer)
        val existing = mObservers.putIfAbsent(observer, wrapper)
        require((existing is AlwaysActiveObserver)) {
            ("Cannot add the same observer"
                    + " with different lifecycles")
        }
    }

    /**
     * Observe all drone data without a lifecycle
     */

    @RequiresApi(Build.VERSION_CODES.N)
    fun observeAutelDroneForever(observer: Observer<Pair<IBaseDevice, T>>) {
        val wrapper = AlwaysAutelDroneActiveObserver(observer)
        val existing = mAutelDroneObservers.putIfAbsent(observer, wrapper)
        require(existing is AlwaysAutelDroneActiveObserver) {
            ("Cannot add the same observer"
                    + " with different lifecycles")
        }
    }

    /**
     * Report data
     */
    fun putValue(droneDevice: IBaseDevice, value: T) {
        map[droneDevice] = value
        if (droneDevice == DeviceManager.getDeviceManager().getFirstDroneDevice()) {
            for (ob in mObservers.values) {
                ob.observer.onChanged(value)
            }
        }
        for (ob in mAutelDroneObservers.values) {
            ob.observer.onChanged(droneDevice to value)
        }
    }

    /**
     * Remove data of a specific drone
     */
    fun remove(droneDevice: IBaseDevice) {
        map.remove(droneDevice)
    }

    /**
     * Remove an observer
     */
    fun removeObserver(observer: Observer<T>) {
        val removed = mObservers.remove(observer) ?: return
        removed.detachObserver()
    }

    /**
     * Remove an observer
     */
    fun removeAutelDroneObserver(observer: Observer<Pair<IBaseDevice, T>>) {
        val removed = mAutelDroneObservers.remove(observer) ?: return
        removed.detachObserver()
    }

    /**
     * Get a specific drone value
     */
    fun getAutelDroneValue(key: IBaseDevice): T? {
        return map[key]
    }

    /**
     * Get the default drone value
     */
    fun getValue(): T? {
        if (DeviceManager.getDeviceManager().getFirstDroneDevice() != null) {
            return map[DeviceManager.getDeviceManager().getFirstDroneDevice()!!]
        }
        return null
    }

    open inner class ObserverWrapper(val observer: Observer<T>) {
        open fun isAttachedTo(owner: LifecycleOwner): Boolean {
            return false
        }

        open fun detachObserver() {

        }
    }

    open inner class ObserverAutelDroneWrapper(val observer: Observer<Pair<IBaseDevice, T>>) {
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

    inner class LifecycleBoundAutelDroneObserver(
        private val mOwner: LifecycleOwner,
        observer: Observer<Pair<IBaseDevice, T>>
    ) :
        ObserverAutelDroneWrapper(observer), LifecycleEventObserver {

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            val currentState = mOwner.lifecycle.currentState
            if (currentState == Lifecycle.State.DESTROYED) {
                removeAutelDroneObserver(observer)
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

    inner class AlwaysAutelDroneActiveObserver internal constructor(observer: Observer<Pair<IBaseDevice, T>>) :
        ObserverAutelDroneWrapper(observer) {

    }


}