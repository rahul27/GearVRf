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

package org.gearvrf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.gearvrf.GVRCursorController.ActiveState;
import org.gearvrf.utility.Log;
import org.joml.Vector3f;

/**
 * This class manages {@link GVRBaseSensor}s
 */
class SensorManager {
    private static final String TAG = SensorManager.class.getSimpleName();
    private static final float[] ORIGIN = new float[]{0.0f, 0.0f, 0.0f};
    private static final float[] EMPTY_HIT_POINT = new float[3];

    // Create a HashMap to keep reference counts
    private final Map<GVRBaseSensor, Integer> sensors;
    private static SensorManager instance;

    private ByteBuffer readbackBufferB;
    private FloatBuffer readbackBuffer;
    // Data size 3 for x, y, z.
    private static int DATA_SIZE = 3;
    private static int BYTE_TO_FLOAT = 4;


    private GVRScene scene;


    private SensorManager() {
        sensors = new ConcurrentHashMap<GVRBaseSensor, Integer>();
        // Create a readback buffer and forward it to the native layer
        readbackBufferB = ByteBuffer.allocateDirect(DATA_SIZE * BYTE_TO_FLOAT);
        readbackBufferB.order(ByteOrder.nativeOrder());
        readbackBuffer = readbackBufferB.asFloatBuffer();
    }

    static SensorManager getInstance() {
        if (instance == null) {
            instance = new SensorManager();
        }
        return instance;
    }

    /**
     * Uses the GVR Picker for now .. but would need help from the renderer
     * later for efficiency.
     *
     * We could possibly push this functionality to the native layer. But for
     * now we keep it here.
     */
    boolean processPick(GVRScene scene, GVRCursorController controller) {
        if (scene != null && !sensors.isEmpty()) {
            boolean markActiveNodes = false;
            if (controller.getActiveState() == ActiveState.ACTIVE_PRESSED) {
                // active is true, trigger a search for active sensors
                markActiveNodes = true;
            } else if (controller
                    .getActiveState() == ActiveState.ACTIVE_RELEASED) {
                for (GVRBaseSensor sensor : sensors.keySet()) {
                    sensor.setActive(controller, false);
                }
            }

            if(isValidRay(controller.getRay())) {
                for (GVRSceneObject object : scene.getSceneObjects()) {
                    recurseSceneObject(controller, object, null, markActiveNodes);
                }
            }
            boolean eventHandled = false;
            for (GVRBaseSensor sensor : sensors.keySet()) {
                eventHandled = sensor.processList(controller);
            }
            return eventHandled;
        }
        return false;
    }

    private boolean isValidRay(Vector3f ray) {
        if(ray == null || Float.isNaN(ray.x) || Float.isNaN(ray.y) || Float.isNaN(ray.z) ||
                (ray.x == 0 && ray.y == 0 && ray.z == 0)) {
            return false;
        } else {
            return true;
        }
    }

    private void recurseSceneObject(GVRCursorController controller,
                                    GVRSceneObject object, GVRBaseSensor sensor,
                                    boolean markActiveNodes) {
        GVRBaseSensor objectSensor = object.getSensor();

        if (objectSensor == null) {
            objectSensor = sensor;
        }
        /**
         * Compare ray against the hierarchical bounding volume and then add
         * the children accordingly.
         */
        Vector3f ray = controller.getRay();
        Vector3f origin = controller.getOrigin();
        if (!object.intersectsBoundingVolume(origin.x, origin.y, origin.z, ray.x, ray.y, ray.z)) {
            return;
        }

        // Well at least we are not comparing against all scene objects.
        if (objectSensor != null && objectSensor.isEnabled() && object.isEnabled()
                & object.hasMesh()) {

            boolean result = GVRPicker.pickSceneObjectAgainstBoundingBox(
                    object, origin.x, origin.y, origin.z, ray.x, ray.y, ray.z, readbackBufferB);

            if (result) {
                objectSensor.addSceneObject(controller, object, readbackBuffer.get(0),
                        readbackBuffer.get(1), readbackBuffer.get(2)
                );

                // if we are doing an active search and we find one.
                if (markActiveNodes) {
                    objectSensor.setActive(controller, true);
                }
            }
        }
        for (GVRSceneObject child : object.getChildren()) {
            recurseSceneObject(controller, child, objectSensor,
                    markActiveNodes);
        }
    }

    void setScene(GVRScene scene) {
        this.scene = scene;
        scene.getEventReceiver().addListener(pickEvents);
        //if (!sensors.isEmpty()) {
        //    scene.getEventReceiver().addListener(pickEvents);
        //}
    }

    void addSensor(GVRBaseSensor sensor) {
       /* if (sensors.isEmpty()) {
            scene.getEventReceiver().addListener(pickEvents);
        }*/

        Integer count = sensors.get(sensor);
        if (count == null) {
            // sensor not in HashMap
            sensors.put(sensor, 1);
        } else {
            // increment count
            sensors.put(sensor, ++count);
        }
    }

    void clear() {
        sensors.clear();
    }

    void removeSensor(GVRBaseSensor sensor) {
        Integer count = sensors.get(sensor);
        if (count == null) {
            // invalid sensor
            return;
        }

        if (count == 1) {
            // last remaining reference, remove
            sensors.remove(sensor);
        } else {
            // decrement count
            sensors.put(sensor, --count);
        }

        /*if (sensors.isEmpty()) {
            scene.getEventReceiver().removeListener(pickEvents);
        }*/
    }

    private IPickEvents pickEvents = new IPickEvents() {
        @Override
        public void onPick(GVRPicker picker) {
            //picker.

        }

        @Override
        public void onNoPick(GVRPicker picker) {

        }

        @Override
        public void onPickEnter(GVRSceneObject sceneObj, GVRPicker picker, GVRPicker
                .GVRPickedObject collision) {
            Log.d(TAG, "onPickEnter");
            GVRBaseSensor objectSensor = sceneObj.getSensor();

            if (objectSensor != null && objectSensor.isEnabled() && sceneObj.isEnabled()) {
                GVRSceneObject pickerObject = picker.getOwnerObject();
                GVRCursorController controller = (GVRCursorController) pickerObject.getComponent
                        (GVRCursorController.getComponentType());
                //objectSensor.addSceneObject(controller, sceneObj, collision.getHitLocation());
                sendSensorEvent(objectSensor, controller, sceneObj, true, collision.getHitLocation());
            }

        }

        @Override
        public void onPickExit(GVRSceneObject sceneObj, GVRPicker picker) {
            GVRBaseSensor objectSensor = sceneObj.getSensor();
            Log.d(TAG, "onPickExit");
            if (objectSensor != null && objectSensor.isEnabled() && sceneObj.isEnabled()) {
                GVRSceneObject pickerObject = picker.getOwnerObject();
                GVRCursorController controller = (GVRCursorController) pickerObject.getComponent
                        (GVRCursorController.getComponentType());
                //objectSensor.addSceneObject(controller, sceneObj, collision.getHitLocation());
                sendSensorEvent(objectSensor, controller, sceneObj, false, EMPTY_HIT_POINT);
            }

        }

        @Override
        public void onInside(GVRSceneObject sceneObj, GVRPicker.GVRPickedObject collision) {
            GVRBaseSensor objectSensor = sceneObj.getSensor();
            GVRPicker picker = collision.getPicker();
            Log.d(TAG, "onInside");
            if (objectSensor != null && objectSensor.isEnabled() && sceneObj.isEnabled()) {
                GVRSceneObject pickerObject = picker.getOwnerObject();
                GVRCursorController controller = (GVRCursorController) pickerObject.getComponent
                        (GVRCursorController.getComponentType());
                //objectSensor.addSceneObject(controller, sceneObj, collision.getHitLocation());
                sendSensorEvent(objectSensor, controller, sceneObj, true, collision.getHitLocation());
            }
        }

        private void sendSensorEvent(GVRBaseSensor baseSensor, GVRCursorController controller,
                                     GVRSceneObject object, boolean isOver,
                                     float[] hitPoint) {
            SensorEvent event = SensorEvent.obtain();
            event.setActive(controller.getActive());
            event.setCursorController(controller);
            event.setObject(object);
            // clear the hit point
            event.setHitPoint(hitPoint[0],hitPoint[1], hitPoint[2]);
            event.setOver(isOver);

            GVREventManager eventManager = object.getGVRContext().getEventManager();

            final IEventReceiver ownerCopy = baseSensor.getOwner();

            eventManager.sendEvent(ownerCopy, ISensorEvents.class, "onSensorEvent", event);
            event.recycle();
        }
    };
}
