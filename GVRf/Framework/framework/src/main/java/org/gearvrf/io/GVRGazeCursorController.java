/* Copyright 2015 Samsung Electronics Co., LTD
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

package org.gearvrf.io;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRDrawFrameListener;
import org.joml.Vector3f;

class GVRGazeCursorController extends GVRBaseController implements
        GVRDrawFrameListener {
    private static final String TAG = GVRGazeCursorController.class
            .getSimpleName();
    private static final int TAP_TIMEOUT = 60;
    private static float TOUCH_SQUARE = 8.0f * 8.0f;
    private static final float DEPTH_SENSITIVITY = 0.1f;
    private final GVRContext context;
    private int referenceCount = 0;
    private boolean buttonDownSent = false;
    private float actionDownX;
    private float actionDownY;
    private float actionDownZ;
    boolean isEnabled = false;

    // Used to calculate the absolute position that the controller reports to
    // the user.
    private final Vector3f gazePosition;
    private Object lock = new Object();
    

    // Saves the relative position of the cursor with respect to the camera.
    private final Vector3f setPosition;
    private EventHandlerThread thread;
    private boolean threadStarted;

    public GVRGazeCursorController(GVRContext context,
                                   GVRControllerType controllerType, String name, int vendorId,
                                   int productId) {
        super(controllerType, name, vendorId, productId);
        this.context = context;
        gazePosition = new Vector3f();
        setPosition = new Vector3f();
        thread = new EventHandlerThread();
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        isEnabled = isEnabled();
=======
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
    }

    /*
     * The increment the reference count to let the cursor controller know how
     * many input devices are using this controller.
     */
    void incrementReferenceCount() {
        referenceCount++;
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        if (referenceCount == 1 && isEnabled) {
            start();
        }
    }


    private void start(){
        if (!threadStarted) {
            thread.start();
            thread.prepareHandler();
            threadStarted = true;
        }
        context.registerDrawFrameListener(this);
    }

    private void stop(){
        context.unregisterDrawFrameListener(this);
        if (threadStarted) {
            thread.quitSafely();
            thread = new EventHandlerThread();
            threadStarted = false;
=======
        if (referenceCount == 1) {
            if(!threadStarted) {
                thread.start();
                thread.prepareHandler();
            }
            context.registerDrawFrameListener(this);

>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
        }
    }

    /*
     * The decrement the reference count to let the cursor controller know how
     * many input devices are using this controller.
     */
    void decrementReferenceCount() {
        referenceCount--;
        // no more devices
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        if (referenceCount == 0 && isEnabled) {
           stop();
=======
        if (referenceCount == 0) {
            context.unregisterDrawFrameListener(this);
            if(threadStarted) {
                thread.quitSafely();
                thread = new EventHandlerThread();
                threadStarted = false;
            }
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
        }
    }

    @Override
    boolean dispatchKeyEvent(KeyEvent event) {
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        if(!threadStarted){
            return false;
        }
=======
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
        thread.dispatchKeyEvent(event);
        return true;
    }

    @Override
    boolean dispatchMotionEvent(MotionEvent event) {
        if(!threadStarted){
            return false;
        }

        MotionEvent clone = MotionEvent.obtain(event);
        float eventX = event.getX();
        float eventY = event.getY();
        int action = clone.getAction();
        Handler handler = thread.getGazeEventHandler();
        if (action == MotionEvent.ACTION_DOWN) {
            actionDownX = eventX;
            actionDownY = eventY;
            actionDownZ = setPosition.z;
            // report ACTION_DOWN as a button
            handler.sendEmptyMessageAtTime(EventHandlerThread.SET_KEY_DOWN, event.getDownTime()
                    + TAP_TIMEOUT);
        } else if (action == MotionEvent.ACTION_UP) {
            // report ACTION_UP as a button
            handler.removeMessages(EventHandlerThread.SET_KEY_DOWN);
            if (buttonDownSent) {
                handler.sendEmptyMessage(EventHandlerThread.SET_KEY_UP);
                buttonDownSent = false;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            float deltaX = eventX - actionDownX;
            float deltaY = eventY - actionDownY;
            float eventZ = actionDownZ + (deltaX * DEPTH_SENSITIVITY);

            if (eventZ >= getNearDepth()) {
                eventZ = getNearDepth();
            }
            if (eventZ <= getFarDepth()) {
                eventZ = getFarDepth();
            }

            synchronized (lock) {
                setPosition.z = eventZ;
            }
            float distance = (deltaX * deltaX) + (deltaY * deltaY);
            if (distance > TOUCH_SQUARE) {
                handler.removeMessages(EventHandlerThread.SET_KEY_DOWN);
            }
        }
        setMotionEvent(clone);
        return true;
    }

    @Override
    public void setEnable(boolean enable) {
        if (!isEnabled && enable) {
            isEnabled = true;
            if (referenceCount > 0) {
                start();
                //set the enabled flag on the handler thread
                thread.setEnabled(true);
            }

        } else if (isEnabled && !enable) {
            isEnabled = false;
            if (referenceCount > 0) {
                if(threadStarted){
                    //set the disabled flag on the handler thread
                    thread.setEnabled(false);
                }
                stop();
            }
        }
    }

    @Override
    public void setPosition(float x, float y, float z) {
        setPosition.set(x, y, z);
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        thread.setPosition(x, y, z);
=======
        thread.setPosition(x,y,z);
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
    }

    @Override
    public void onDrawFrame(float frameTime) {
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        synchronized (lock) {
            setPosition.mulPosition(context.getMainScene().getMainCameraRig()
                    .getHeadTransform().getModelMatrix4f(), gazePosition);
        }
        thread.setPosition(gazePosition.x, gazePosition.y, gazePosition.z);
=======
        if(isEnabled()) {
            synchronized (lock) {
                setPosition.mulPoint(context.getMainScene().getMainCameraRig()
                        .getHeadTransform().getModelMatrix4f(), gazePosition);
            }
            thread.setPosition(gazePosition.x, gazePosition.y, gazePosition.z);
        }
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
    }

    void close() {
        // unregister the draw frame listener
        if (referenceCount > 0) {
            context.unregisterDrawFrameListener(this);
        }
        referenceCount = 0;
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        if (threadStarted) {
=======
        if(threadStarted) {
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
            thread.quitSafely();
        }
    }

    private class EventHandlerThread extends HandlerThread {
        private static final String THREAD_NAME = "GVRGazeEventHandlerThread";
        public static final int SET_POSITION = 0;
        public static final int SET_KEY_EVENT = 1;
        public static final int SET_KEY_DOWN = 2;
        public static final int SET_KEY_UP = 3;
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        public static final int SET_ENABLE = 4;
        public static final int ENABLE = 0;
        public static final int DISABLE = 1;

=======
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
        private final KeyEvent BUTTON_GAZE_DOWN = new KeyEvent(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_1);
        private final KeyEvent BUTTON_GAZE_UP = new KeyEvent(KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_BUTTON_1);
        private Handler gazeEventHandler;
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
        private final Vector3f position;

        public EventHandlerThread() {
            super(THREAD_NAME);
            position = new Vector3f();
=======
        public EventHandlerThread() {
            super(THREAD_NAME);
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
        }

        public void prepareHandler() {
            gazeEventHandler = new Handler(getLooper(), new Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    switch (msg.what) {
                        case SET_POSITION:
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
                            float x, y, z;
                            synchronized (position) {
                                x = position.x;
                                y = position.y;
                                z = position.z;
                            }
                            GVRGazeCursorController.super.setPosition(x, y, z);
=======
                            float[] position = (float[])msg.obj;
                            GVRGazeCursorController.super.setPosition(position[0],position[1],
                                    position[2]);
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
                            break;
                        case SET_KEY_DOWN:
                            buttonDownSent = true;
                            setKeyEvent(BUTTON_GAZE_DOWN);
                            break;
                        case SET_KEY_UP:
                            setKeyEvent(BUTTON_GAZE_UP);
                            break;
                        case SET_KEY_EVENT:
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
                            KeyEvent keyEvent = (KeyEvent) msg.obj;
                            setKeyEvent(keyEvent);
                            break;
                        case SET_ENABLE:
                            GVRGazeCursorController.super.setEnable(msg.arg1 == ENABLE);
                            break;
=======
                            KeyEvent keyEvent = (KeyEvent)msg.obj;
                            setKeyEvent(keyEvent);
                            break;
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
                    }
                    return false;
                }
            });
        }

        public void setPosition(float x, float y, float z) {
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
            synchronized (position) {
                position.x = x;
                position.y = y;
                position.z = z;
            }
            Message msg = Message.obtain(gazeEventHandler, SET_POSITION, position);
            gazeEventHandler.removeMessages(SET_POSITION);
=======
            Message msg = Message.obtain(gazeEventHandler,SET_POSITION,new float[]{x,y,z});
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
            msg.sendToTarget();
        }

        public void dispatchKeyEvent(KeyEvent keyEvent) {
<<<<<<< 4fe311926f5a829bbd2a92a985a718a6bc03ba89
            Message msg = Message.obtain(gazeEventHandler, SET_KEY_EVENT, keyEvent);
            msg.sendToTarget();
        }

        public void setEnabled(boolean enable) {
            gazeEventHandler.removeMessages(SET_ENABLE);
            Message msg = Message.obtain(gazeEventHandler, SET_ENABLE, enable ? ENABLE : DISABLE);
            msg.sendToTarget();
        }

=======
            Message msg = Message.obtain(gazeEventHandler,SET_KEY_EVENT,keyEvent);
            msg.sendToTarget();
        }
>>>>>>> Fixes for 3DCursorLibrary and org.gearvrf.io
        public Handler getGazeEventHandler() {
            return gazeEventHandler;
        }
    }
}
