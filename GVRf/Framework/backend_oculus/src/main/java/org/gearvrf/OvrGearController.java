/*
/* Copyright 2017 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf;

import android.graphics.PointF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.KeyEvent;

import org.gearvrf.io.CursorControllerListener;
import org.gearvrf.io.GVRControllerType;

import org.gearvrf.io.GVRInputManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * This class represents the Gear Controller.
 *
 * The input manager notifies the application when the controller has connected successfully. Use
 * the {@link GVRInputManager#addCursorControllerListener(CursorControllerListener)} to get notified
 * when the controller is available to use. To query the device specific information from the
 * Gear Controller make sure to type cast the returned {@link GVRCursorController} to
 * {@link OvrGearController} like below:
 *
 * <code>
 * OvrGearController controller = (OvrGearController) gvrCursorController;
 * </code>
 *
 * Additionally register a {@link ControllerEventListener} using
 * {@link GVRCursorController#addControllerEventListener(ControllerEventListener)} to receive
 * notification whenever the controller information is updated.
 */
public class OvrGearController extends GVRCursorController {
    private static final String TAG = OvrGearController.class.getSimpleName();

    private static final int DATA_SIZE = 12;
    private static final int BYTE_TO_FLOAT = 4;
    private static final int INDEX_CONNECTED = 0;
    private static final int INDEX_HANDEDNESS = 1;
    private static final int INDEX_POSITION = 2;
    private static final int INDEX_ROTATION = 5;
    private static final int INDEX_BUTTON = 9;
    private static final int INDEX_TOUCHPAD = 10;
    private static final int OVR_BUTTON_A = 0x00000001;
    private static final int OVR_BUTTON_ENTER = 0x00100000;
    private static final int OVR_BUTTON_BACK = 0x00200000;

    /**
     * Defines the handedness of the gear controller.
     */
    public enum Handedness {
        LEFT, RIGHT
    }

    private GVRSceneObject pivot;
    private GVRContext context;
    private FloatBuffer readbackBuffer;

    private EventHandlerThread thread;
    private boolean initialized = false;
    private final long mPtr;
    private boolean isEnabled = false;

    OvrGearController(GVRContext context) {
        super(GVRControllerType.CONTROLLER);
        ByteBuffer readbackBufferB = ByteBuffer.allocateDirect(DATA_SIZE * BYTE_TO_FLOAT);
        readbackBufferB.order(ByteOrder.nativeOrder());
        readbackBuffer = readbackBufferB.asFloatBuffer();
        mPtr = OvrNativeGearController.ctor(readbackBufferB);
        this.context = context;
        pivot = new GVRSceneObject(context);
        thread = new EventHandlerThread();
        isEnabled = isEnabled();
    }


    long getPtr() {
        return mPtr;
    }

    @Override
    public void setSceneObject(GVRSceneObject object) {
        if(pivot.getParent() != context.getMainScene().getRoot()) {
            context.getMainScene().addSceneObject(pivot);
        }
        pivot.addChildObject(object);
    }

    @Override
    public void resetSceneObject() {
        if(pivot.getParent() == context.getMainScene().getRoot()) {
            context.getMainScene().removeSceneObject(pivot);
        }
        for(GVRSceneObject child : pivot.getChildren()){
            pivot.removeChildObject(child);
        }
    }

    @Override
    public void setEnable(boolean enable) {
        if (!isEnabled && enable) {

            if (initialized) {
                //set the enabled flag on the handler thread
                isEnabled = true;
                thread.setEnabled(true);
            }
        } else if (isEnabled && !enable) {
            if (initialized) {
                isEnabled = false;
                //set the disabled flag on the handler thread
                thread.setEnabled(false);
            }
        }
    }

    @Override
    protected void setScene(GVRScene scene) {
        if (!initialized) {
            super.setScene(scene);
        } else {
            thread.setScene(scene);
        }
    }

    @Override
    public void invalidate() {
        if (!initialized) {
            //do nothing
            return;
        }
        thread.sendInvalidate();
    }

    void onDrawFrame() {
        boolean connected = readbackBuffer.get(INDEX_CONNECTED) == 1.0f;
        if (connected && isEnabled()) {
            if (!initialized) {
                thread.start();
                thread.prepareHandler();
                thread.initialize();
                initialized = true;
            }

            ControllerEvent event = ControllerEvent.obtain();

            event.rotation.set(readbackBuffer.get(INDEX_ROTATION + 1),
                    readbackBuffer.get(INDEX_ROTATION + 2),
                    readbackBuffer.get(INDEX_ROTATION + 3),
                    readbackBuffer.get(INDEX_ROTATION));
            event.position.set(readbackBuffer.get(INDEX_POSITION),
                    readbackBuffer.get(INDEX_POSITION + 1),
                    readbackBuffer.get(INDEX_POSITION + 2));
            event.key = (int) readbackBuffer.get(INDEX_BUTTON);
            event.handedness = readbackBuffer.get(INDEX_HANDEDNESS);
            event.pointF.set(readbackBuffer.get(INDEX_TOUCHPAD),
                    readbackBuffer.get(INDEX_TOUCHPAD + 1));

            thread.sendEvent(event);
        } else {
            if (initialized) {
                thread.uninitialize();
                thread.quitSafely();
                thread = new EventHandlerThread();
                initialized = false;
            }
        }
    }

    /**
     * Return the current position of the Gear Controller.
     *
     * @return a {@link Vector3f} representing the position of the controller. This function
     * returns <code>null</code> if the controller is unavailable or the data is stale.
     */
    public Vector3f getPosition() {
        if (thread == null || thread.currentControllerEvent == null || thread
                .currentControllerEvent.isRecycled()) {
            return null;
        }
        return thread.currentControllerEvent.position;
    }

    /**
     * Return the current rotation of the Gear Controller.
     *
     * @return a {@link Quaternionf} representing the rotation of the controller. This function
     * returns <code>null</code> if the controller is unavailable or the data is stale.
     */
    public Quaternionf getRotation() {
        if (thread == null || thread.currentControllerEvent == null || thread
                .currentControllerEvent.isRecycled()) {
            return null;
        }
        return thread.currentControllerEvent.rotation;
    }

    /**
     * Return the current touch coordinates of the Gear Controller touchpad.
     *
     * @return a {@link PointF} representing the touch coordinates on the controller. If the
     * user is not using the touchpad (0.0f, 0.0f) is returned. This function
     * returns <code>null</code> if the controller is unavailable or the data is stale.
     */
    public PointF getTouch() {
        if (thread == null || thread.currentControllerEvent == null || thread
                .currentControllerEvent.isRecycled()) {
            return null;
        }
        return thread.currentControllerEvent.pointF;
    }

    /**
     * Return the current handedness of the Gear Controller.
     *
     * @return returns whether the user is using the controller left or right handed. This function
     * returns <code>null</code> if the controller is unavailable or the data is stale.
     */
    public Handedness getHandedness() {
        if (thread == null || thread.currentControllerEvent == null || thread
                .currentControllerEvent.isRecycled()) {
            return null;
        }
        return thread.currentControllerEvent.handedness == 0.0f ? Handedness.LEFT : Handedness
                .RIGHT;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (initialized) {
            thread.uninitialize();
            thread.quitSafely();
            initialized = false;
        }
        OvrNativeGearController.delete(mPtr);
    }

    private class EventHandlerThread extends HandlerThread {
        private static final String THREAD_NAME = "GVREventHandlerThread";
        private final Vector3f FORWARD = new Vector3f(0.0f, 0.0f, -1.0f);
        private Handler handler;
        private Vector3f result = new Vector3f();
        private int prevButtonEnter = KeyEvent.ACTION_UP;
        private int prevButtonA = KeyEvent.ACTION_UP;
        private int prevButtonBack = KeyEvent.ACTION_UP;
        private static final int MSG_INITIALIZE = 1;
        private static final int MSG_UNINITIALIZE = 2;
        private static final int MSG_EVENT = 3;
        public static final int MSG_SET_ENABLE = 4;
        public static final int MSG_SET_SCENE = 5;
        public static final int MSG_SEND_INVALIDATE = 6;

        public static final int ENABLE = 0;
        public static final int DISABLE = 1;

        private ControllerEvent currentControllerEvent;

        EventHandlerThread() {
            super(THREAD_NAME);
        }

        void prepareHandler() {
            handler = new Handler(getLooper(), new Handler.Callback() {
                @Override
                public boolean handleMessage(Message message) {
                    switch (message.what) {
                        case MSG_INITIALIZE:
                            context.getInputManager().addCursorController(OvrGearController.this);
                            break;
                        case MSG_EVENT:
                            handleControllerEvent((ControllerEvent) message.obj);
                            break;
                        case MSG_UNINITIALIZE:
                            context.getInputManager().removeCursorController(OvrGearController
                                    .this);
                            break;
                        case MSG_SET_ENABLE:
                            OvrGearController.super.setEnable(message.arg1 == ENABLE);
                            break;
                        case MSG_SET_SCENE:
                            OvrGearController.super.setScene((GVRScene) message.obj);
                            break;
                        case MSG_SEND_INVALIDATE:
                            OvrGearController.super.invalidate();
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            });
        }

        void handleControllerEvent(ControllerEvent event) {
            this.currentControllerEvent = event;
            Quaternionf quaternionf = event.rotation;
            Vector3f position = event.position;
            int key = event.key;
            event.rotation.normalize();
            pivot.getTransform().setRotation(quaternionf.w, quaternionf.x, quaternionf.y,
                    quaternionf.z);

            quaternionf.transform(FORWARD, result);

            pivot.getTransform().setPosition(position.x, position.y, position.z);
            setOrigin(position.x, position.y, position.z);

            int handleResult = handleButton(key, OVR_BUTTON_ENTER, prevButtonEnter, KeyEvent
                    .KEYCODE_ENTER);
            prevButtonEnter = handleResult == -1 ? prevButtonEnter : handleResult;

            handleResult = handleButton(key, OVR_BUTTON_A, prevButtonA, KeyEvent
                    .KEYCODE_A);
            prevButtonA = handleResult == -1 ? prevButtonA : handleResult;

            handleResult = handleButton(key, OVR_BUTTON_BACK, prevButtonBack, KeyEvent
                    .KEYCODE_BACK);
            prevButtonBack = handleResult == -1 ? prevButtonBack : handleResult;

            setPosition(result.x, result.y, result.z);
            event.recycle();
        }

        void sendEvent(ControllerEvent event) {
            handler.sendMessage(Message.obtain(null, MSG_EVENT, event));
        }

        void initialize() {
            handler.sendMessage(Message.obtain(null, MSG_INITIALIZE));
        }

        void uninitialize() {
            handler.sendMessage(Message.obtain(null, MSG_UNINITIALIZE));
        }

        public void setEnabled(boolean enable) {
            handler.removeMessages(MSG_SET_ENABLE);
            Message msg = Message.obtain(handler, MSG_SET_ENABLE, enable ? ENABLE : DISABLE);
            msg.sendToTarget();
        }

        void setScene(GVRScene scene){
            handler.removeMessages(MSG_SET_SCENE);
            Message msg = Message.obtain(handler, MSG_SET_SCENE, scene);
            msg.sendToTarget();
        }

        void sendInvalidate(){
            handler.removeMessages(MSG_SEND_INVALIDATE);
            Message msg = Message.obtain(handler, MSG_SEND_INVALIDATE);
            msg.sendToTarget();
        }
    }

    private int handleButton(int key, int buttonType, int prevButton, int keyCode) {
        if ((key & buttonType) != 0) {
            if (prevButton != KeyEvent.ACTION_DOWN) {
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                setKeyEvent(keyEvent);
                return KeyEvent.ACTION_DOWN;
            }
        } else {
            if (prevButton != KeyEvent.ACTION_UP) {
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
                setKeyEvent(keyEvent);
                return KeyEvent.ACTION_UP;
            }
        }
        return -1;
    }

    private static class ControllerEvent {
        private static final int MAX_RECYCLED = 5;
        private static final Object recyclerLock = new Object();
        private static int recyclerUsed;
        private static ControllerEvent recyclerTop;
        private ControllerEvent next;
        private Quaternionf rotation = new Quaternionf();
        private Vector3f position = new Vector3f();
        private PointF pointF = new PointF();
        private int key;
        private float handedness;
        private boolean recycled = false;

        static ControllerEvent obtain() {
            final ControllerEvent event;
            synchronized (recyclerLock) {
                event = recyclerTop;
                if (event == null) {
                    return new ControllerEvent();
                }
                event.recycled = false;
                recyclerTop = event.next;
                recyclerUsed -= 1;
            }
            event.next = null;
            return event;
        }

        final void recycle() {
            synchronized (recyclerLock) {
                if (recyclerUsed < MAX_RECYCLED) {
                    recyclerUsed++;
                    next = recyclerTop;
                    recyclerTop = this;
                    recycled = true;
                }
            }
        }

        boolean isRecycled() {
            return recycled;
        }
    }
}

class OvrNativeGearController {
    static native long ctor(ByteBuffer buffer);

    static native void delete(long jConfigurationManager);
}