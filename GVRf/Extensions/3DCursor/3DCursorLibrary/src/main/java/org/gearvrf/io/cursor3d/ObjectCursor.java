/*
 * Copyright 2016 Samsung Electronics Co., LTD
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

package org.gearvrf.io.cursor3d;

import android.view.KeyEvent;

import org.gearvrf.GVRBaseSensor;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRCursorController;
import org.gearvrf.GVRCursorController.ControllerEventListener;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRSceneObject.BoundingVolume;
import org.gearvrf.SensorEvent;
import org.gearvrf.io.cursor3d.CursorAsset.Action;
import org.gearvrf.scene_objects.GVRCubeSceneObject;
import org.gearvrf.utility.Log;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ObjectCursor extends Cursor {
    private static final String TAG = ObjectCursor.class.getSimpleName();
    private static final float POINT_CURSOR_NEAR_DEPTH = -1.0f;
    private static final float[] EMPTY_HIT_POINT = new float[3];
    private Set<GVRSceneObject> intersecting;
    private Set<GVRSceneObject> previousHits;
    private List<GVRSceneObject> newHits;
    private boolean active;
    private Matrix4f matrix4f;

    ObjectCursor(GVRContext context, CursorManager cursorManager) {
        super(context, CursorType.OBJECT, cursorManager);
        intersecting = new HashSet<GVRSceneObject>();
        previousHits = new HashSet<GVRSceneObject>();
        newHits = new ArrayList<GVRSceneObject>();
        matrix4f = new Matrix4f();
    }

    @Override
    void dispatchSensorEvent(SensorEvent event) {
        GVRSceneObject object = event.getObject();

        //GVRSceneObject sensorObject = getSensorObject(object);
        //if (sensorObject != null) {
            if (intersecting.contains(object)) {
                createAndSendCursorEvent(object, true, event.getHitPoint(), true, active,
                        event.getCursorController().getKeyEvent());
            } else {
                createAndSendCursorEvent(object, false, event.getHitPoint(), event.isOver
                        (), active, event.getCursorController().getKeyEvent());
            }
        //}
    }

    private void createAndSendCursorEvent(GVRSceneObject sceneObject, boolean colliding, float[]
            hitPoint, boolean isOver, boolean isActive, KeyEvent keyEvent) {
        CursorEvent cursorEvent = CursorEvent.obtain();
        cursorEvent.setColliding(colliding);
        cursorEvent.setHitPoint(hitPoint);
        cursorEvent.setOver(isOver);
        cursorEvent.setObject(sceneObject);
        cursorEvent.setActive(isActive);
        cursorEvent.setCursor(this);
        cursorEvent.setKeyEvent(keyEvent);

        if (intersecting.isEmpty() == false) {
            if (isActive) {
                checkAndSetAsset(Action.CLICK);
            } else {
                checkAndSetAsset(Action.INTERSECT);
            }
        } else {
            checkAndSetAsset(Action.DEFAULT);
        }

        dispatchCursorEvent(cursorEvent);
    }

    private boolean pointInBox(float x, float y, float z, Vector3f cubeMin, Vector3f cubeMax) {
        return (x < cubeMin.x || y < cubeMin.y || z < cubeMin.z
                || x > cubeMax.x || y > cubeMax.y || z > cubeMax.z) == false;
    }

    @Override
    ControllerEventListener getControllerEventListener() {
        return listener;
    }

    private ControllerEventListener listener = new ControllerEventListener() {
        @Override
        public void onEvent(GVRCursorController controller) {
            if (scene == null) {
                return;
            }

            lookAt();

            KeyEvent keyEvent = controller.getKeyEvent();
            if (keyEvent != null) {
                active = (keyEvent.getAction() == KeyEvent.ACTION_DOWN);
            }

            newHits.clear();

            for (GVRSceneObject object : scene.getSceneObjects()) {
                recurseSceneObject(keyEvent, object, null);
            }

            for (GVRSceneObject object : previousHits) {
                if (intersecting.contains(object)) {
                    intersecting.remove(object);
                }
                createAndSendCursorEvent(object, false, EMPTY_HIT_POINT, false, active, keyEvent);
            }
            previousHits.clear();
            previousHits.addAll(newHits);
        }
    };

    private void recurseSceneObject(KeyEvent keyEvent, GVRSceneObject object, GVRBaseSensor
            sensor) {
        GVRBaseSensor objectSensor = object.getSensor();
        if (objectSensor != null) {
            //if (sensorObject != null) {
            //    objectSensor = sensorObject.getSensor();
           // }
            sensor = objectSensor;
        } else {
            //sensorObject = object;
        }

        if (objectSensor != null && objectSensor.isEnabled()) {
            if (object.hasMesh()) {
                matrix4f.set(object.getTransform().getModelMatrix());
                GVRMesh mesh = object.getRenderData().getMesh().getBoundingBox();

                float[] vertices = mesh.getVertices();
                int length = vertices.length;

                Vector3f cubeMin = new Vector3f(vertices[0], vertices[1], vertices[2]);
                Vector3f cubeMax = new Vector3f(vertices[length - 3], vertices[length - 2],
                        vertices[length - 1]);

                cubeMin.mulPoint(matrix4f);
                cubeMax.mulPoint(matrix4f);
                Vector3f objectPosition = new Vector3f(object.getTransform().getPositionX(),
                        object.getTransform().getPositionY(), object.getTransform().getPositionZ());
                objectPosition.mulPoint(matrix4f);
                //GVRSceneObject sensorObject = getSensorObject(object);

                if (!pointInBox(cursorSceneObject.getPositionX(), cursorSceneObject.getPositionY(),
                        cursorSceneObject.getPositionZ(), cubeMin, cubeMax)) {

                    if (isColliding(object)) {

                        //if (sensorObject != null) {
                            addNewHit(object);
                            float[] hitpoint = new float[3];
                            hitpoint[0] = cursorSceneObject.getPositionX() - objectPosition.x;
                            hitpoint[1] = cursorSceneObject.getPositionY() - objectPosition.y;
                            hitpoint[2] = cursorSceneObject.getPositionZ() - objectPosition.z;
                            createAndSendCursorEvent(object, true, hitpoint, true, active,
                                    keyEvent);
                        //}
                    }
                } else {
                    //if (sensorObject != null) {
                        addNewHit(object);
                    //}
                }
            }
        }

        for (GVRSceneObject child : object.getChildren()) {
            recurseSceneObject(keyEvent, child, sensor);
        }
    }

    private GVRSceneObject getSensorObject(GVRSceneObject sceneObject) {
        if (sceneObject == null) {
            return null;
        } else {
            GVRBaseSensor sensor = sceneObject.getSensor();
            if (sensor == null) {
                return getSensorObject(sceneObject.getParent());
            } else {
                return sceneObject;
            }
        }
    }

    private void addNewHit(GVRSceneObject object) {
        if (previousHits.contains(object)) {
            previousHits.remove(object);
        }

        if (intersecting.contains(object) == false) {
            intersecting.add(object);
        }

        newHits.add(object);
    }

    @Override
    void setScale(float scale) {
        // place the cursor at half the depth scale
        super.setScale(scale / 2);

        if (ioDevice != null) {
            ioDevice.setNearDepth(POINT_CURSOR_NEAR_DEPTH);
        }
    }

    @Override
    void setIoDevice(IoDevice ioDevice) {
        super.setIoDevice(ioDevice);
        ioDevice.setNearDepth(POINT_CURSOR_NEAR_DEPTH);
    }

    void destroyIoDevice(IoDevice ioDevice) {
        super.destroyIoDevice(ioDevice);
        if (active) {
            active = false;
        }
    }
}
