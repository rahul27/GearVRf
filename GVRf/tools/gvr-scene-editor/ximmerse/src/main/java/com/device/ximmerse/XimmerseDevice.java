/*
 * Copyright (c) 2016. Samsung Electronics Co., LTD
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

package com.device.ximmerse;

import android.os.RemoteException;
import android.view.KeyEvent;

import com.ximmerse.input.ControllerInput;
import com.ximmerse.input.PositionalTracking;
import com.ximmerse.sdk.XDeviceApi;
import com.ximmerse.sdk.XDeviceConstants;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.io.cursor3d.IoDevice;
import org.gearvrf.scene_objects.GVRConeSceneObject;
import org.gearvrf.scene_objects.GVRSphereSceneObject;
import org.gearvrf.utility.Log;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class XimmerseDevice {
    private static final String TAG = XimmerseDevice.class.getSimpleName();
    private static final String NAME = "Ximmerse";
    private static final String LEFT = "XCobra-0";
    private static final String RIGHT = "XCobra-1";
    private static final String HEAD = "XHawk-0";
    private static final String LEFT_DEVICE_ID = "ximmerse-0";
    private static final String RIGHT_DEVICE_ID = "ximmerse-1";
    //Change this value to control the maximum depth for the cursor
    private static final int MAX_DEPTH = 20;
    //_VENDOR_TODO_ enter device details below
    private static final int VENDOR_ID = 1234;
    private static final int PRODUCT_ID = 5678;
    private static final String VENDOR_NAME = "Ximmerse";

    private GVRContext context;
    private Thread thread;
    private XimmerseIODevice[] devices;
    private PositionalTracking mHeadTrack;
    private int handleHeadTrack;
    private GVRScene scene;

    public XimmerseDevice(GVRContext context, GVRScene scene) {
        this.context = context;
        this.scene = scene;
        XDeviceApi.init(context.getActivity());
        // Set the initial position for the cursor here
        devices = new XimmerseIODevice[2];
        handleHeadTrack = XDeviceApi.getInputDeviceHandle(HEAD);
        mHeadTrack = new PositionalTracking(handleHeadTrack, HEAD);
        devices[0] = new XimmerseIODevice(LEFT_DEVICE_ID, LEFT, scene);
        devices[1] = new XimmerseIODevice(RIGHT_DEVICE_ID, RIGHT, scene);
        Log.d(TAG, "Creating a new thread");
        thread = new Thread(threadRunnable);
        thread.start();
    }

    private volatile boolean running = true;
    private Vector3f initPosition;
    private Runnable threadRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                while (running) {
                    Thread.sleep(5);
                    mHeadTrack.updateState();
                    mHeadTrack.timestamp = 1;
                    float xHead = mHeadTrack.getPositionX(2);
                    float yHead = mHeadTrack.getPositionY(2);
                    float zHead = mHeadTrack.getPositionZ(2);
                    if (xHead == 0 && yHead == 0 && zHead == 0) {
                        continue;
                    }

                    if (initPosition == null) {
                        initPosition = new Vector3f();
                        initPosition.set(xHead, yHead, zHead);
                    }
                    for (int i = 0; i < 2; i++) {
                        if (devices[i].isConnectedCheck()) {
                            devices[i].dispatchInputEvent((initPosition.x - xHead), -
                                            (initPosition.y - yHead), (initPosition.z - zHead),
                                    initPosition);
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Exception", e);
            }
        }
    };

    public List<IoDevice> getDeviceList() {
        List<IoDevice> returnList = new ArrayList<IoDevice>();
        returnList.add(devices[0]);
        returnList.add(devices[1]);
        return returnList;
    }

    private static class XimmerseIODevice extends IoDevice {
        private int handleController;
        boolean connected = false;
        private ControllerInput controllerInput;
        float[] position = new float[3];
        float[] rotation = new float[4];
        private static int OFFSET = 0;

        private GVRScene scene;
        private Quaternionf initialRotation;
        private Quaternionf yawError = new Quaternionf();

        protected XimmerseIODevice(String deviceId, String deviceName, GVRScene scene) {
            super(deviceId, VENDOR_ID, PRODUCT_ID, deviceName, VENDOR_NAME, true);
            this.scene = scene;
            handleController = XDeviceApi.getInputDeviceHandle(deviceName);
            controllerInput = new ControllerInput(handleController, deviceName);
            int status = XDeviceApi.getInt(handleController, XDeviceConstants
                    .kField_ConnectionState, 0);
            connected = (status == XDeviceConstants.kConnectionState_Connected);
            //if(connected){
            //    setConnected();
            //}
        }

        int prevKeyAction = KeyEvent.ACTION_UP;

        protected void dispatchInputEvent(float xHead, float yHead, float zHead, Vector3f
                initPosition) throws
                RemoteException {
            controllerInput.updateState();
            controllerInput.getPosition(position, OFFSET);

            //x,y,z,w
            controllerInput.getRotation(rotation, OFFSET);
            scene.getMainCameraRig().getHeadTransform().setPosition((xHead) * 10.0f, (
                    yHead) * 10.0f, (zHead) * 10.0f);
            Quaternionf rotq = new Quaternionf(rotation[0], rotation[1], rotation[2],
                    rotation[3]);

            if (rotation[0] == 0) {
                return;
            }
            if (initialRotation == null) {

                initialRotation = new Quaternionf();
                initialRotation.set(rotq);
                Vector3f angles = new Vector3f();
                initialRotation.getEulerAnglesXYZ(angles);
                yawError.rotationY(angles.y);
                yawError.invert();
            }

            yawError.mul(rotq, rotq);

            if (position[0] == 0) {
                Log.d(TAG, String.format("no controller position"));
                return;
            }

            int keyAction;
            int keyCode = KeyEvent.KEYCODE_BUTTON_1;

            if (controllerInput.getButton(ControllerInput.BUTTON_LEFT_TRIGGER)) {
                keyAction = KeyEvent.ACTION_DOWN;
                if (prevKeyAction != KeyEvent.ACTION_DOWN) {
                    KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
                    setKeyEvent(keyEvent);
                    prevKeyAction = KeyEvent.ACTION_DOWN;
                }
            } else {
                keyAction = KeyEvent.ACTION_UP;
                if (prevKeyAction != KeyEvent.ACTION_UP) {
                    KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
                    setKeyEvent(keyEvent);
                    prevKeyAction = KeyEvent.ACTION_UP;
                }
            }

            setPosition((initPosition.x - position[0]) * 10.0f, -(initPosition.y - position[1]
            ) * 10.0f, (initPosition.z - position[2]) * 10.0f);

            setRotation(rotq.w, rotq.x, rotq.y, rotq.z);
        }

        boolean isConnectedCheck() {
            XDeviceApi.updateInputState(handleController);
            int status = XDeviceApi.getInt(handleController, XDeviceConstants
                    .kField_ConnectionState, 0);
            if (!connected && (status == XDeviceConstants.kConnectionState_Connected)) {
                android.util.Log.d(TAG, "Connected");
                connected = true;
                //setConnected();
            } else if (connected && (status != XDeviceConstants.kConnectionState_Connected)) {
                android.util.Log.d(TAG, "Disconnected");
                connected = false;
                //setDisconnected();
            }
            return (status == XDeviceConstants.kConnectionState_Connected);
        }

        @Override
        public void setEnable(boolean enable) {
            // When set to disable (i.e. enable == false) the calls to setPosition and setKeyEvents
            // are ignored by the framework. It is recommended that the events processing be put on
            // hold.
            super.setEnable(enable);
        }
    }

    /**
     * Perform cleanup using this method.
     */
    public void close() {
        Log.d(TAG, "Interrupting thread");
        running = false;
        XDeviceApi.exit();
        XDeviceApi.sDeviceManager.onPause();
        XDeviceApi.sDeviceManager.stop();
        XDeviceApi.sContext = null;
        XDeviceApi.sDeviceManager = null;
        XDeviceApi.sJuggler.context = null;
        XDeviceApi.sJuggler = null;
        context = null;
    }
}


