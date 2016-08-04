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

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.gearvrf.GVRBaseSensor;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRCursorController;
import org.gearvrf.GVRCursorController.ControllerEventListener;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRSwitch;
import org.gearvrf.SensorEvent;
import org.gearvrf.io.cursor3d.CursorAsset.Action;


import org.gearvrf.scene_objects.GVRCubeSceneObject;



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
    private Vector3f cubeMin, cubeMax, objectPosition;
    private float[] hitpoint;


    ObjectCursor(GVRContext context, CursorManager cursorManager) {
        super(context, CursorType.OBJECT, cursorManager);
        intersecting = new HashSet<GVRSceneObject>();
        previousHits = new HashSet<GVRSceneObject>();
        newHits = new ArrayList<GVRSceneObject>();
        matrix4f = new Matrix4f();
        cubeMin = new Vector3f();
        cubeMax = new Vector3f();
        objectPosition = new Vector3f();
        hitpoint = new float[3];
    }

    @Override
    void dispatchSensorEvent(SensorEvent event) {
        GVRSceneObject object = event.getObject();

/*        GVRCursorController controller = event.getCursorController();
        isControllerActive = event.isActive();

        if (intersecting.contains(object)) {
            createAndSendCursorEvent(event.getObject(), true, event.getHitX(), event.getHitY(),
                    event.getHitZ(), true, isControllerActive, controller.getKeyEvent(),
                    controller.getMotionEvents());
        } else {
            createAndSendCursorEvent(event.getObject(), false, event.getHitX(), event.getHitY(),
                    event.getHitZ(), event.isOver(), isControllerActive,
                    controller.getKeyEvent(), controller.getMotionEvents());*/


        boolean colliding = false;

        if( isColliding(object)){
            colliding = true;
        }

        SelectableGroup selectableBehavior = (SelectableGroup) object.getComponent
                (SelectableGroup.getComponentType());
        if (selectableBehavior != null) {
            if(colliding){
                selectableBehavior.intersectingList.add(object);

            }else{
                selectableBehavior.intersectingList.remove(object);
                if(!selectableBehavior.intersectingList.isEmpty()){
                    colliding = true;
                }else{
                    colliding = false;
                }
            }
            object = selectableBehavior.getParent();

        }

      /*  if (object != null && colliding) {
            createAndSendCursorEvent(object, true, event.getHitPoint(), true, event.isActive(),
                    event.getCursorController().getKeyEvent());
        } else {
            createAndSendCursorEvent(object, false, event.getHitPoint(), event.isOver

                    (), active, event.getCursorController().getKeyEvent());

        }*/
    }

    private void createAndSendCursorEvent(GVRSceneObject sceneObject, boolean colliding, float
            hitX, float hitY, float hitZ, boolean isOver, boolean isActive, KeyEvent keyEvent,
                                          List<MotionEvent> motionEvents) {
/*
                    (), event.isActive(), event.getCursorController().getKeyEvent());
        }
    }

    private void createAndSendCursorEvent(GVRSceneObject sceneObject, boolean colliding, float[]
            hitPoint, boolean isOver, boolean isActive, KeyEvent keyEvent) {
*/

        CursorEvent cursorEvent = CursorEvent.obtain();
        cursorEvent.setColliding(colliding);
        cursorEvent.setHitPoint(hitX, hitY, hitZ);
        cursorEvent.setCursorPosition(getPositionX(), getPositionY(), getPositionZ());
        cursorEvent.setCursorRotation(getRotationW(), getRotationX(), getRotationY(),
                getRotationZ());
        cursorEvent.setOver(isOver);
        cursorEvent.setObject(sceneObject);
        cursorEvent.setActive(isActive);
        cursorEvent.setCursor(this);
        cursorEvent.setKeyEvent(keyEvent);
        cursorEvent.setMotionEvents(motionEvents);

        if (colliding) {
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

            boolean sentEvent = false;
            newHits.clear();

            KeyEvent keyEvent = controller.getKeyEvent();
            if (!controller.isEventHandledBySensorManager()) {
                checkControllerActive(controller);

                //lookAt();

            /*KeyEvent keyEvent = controller.getKeyEvent();
            if (keyEvent != null) {
                active = (keyEvent.getAction() == KeyEvent.ACTION_DOWN);

            }

            for (GVRSceneObject object : scene.getSceneObjects()) {
                sentEvent = sentEvent || recurseSceneObject(keyEvent, object, null);
            }

            handleControllerEvent(controller, sentEvent);

            for (GVRSceneObject object : previousHits) {
                if (intersecting.contains(object)) {
                    intersecting.remove(object);
                }
                createAndSendCursorEvent(object, false, EMPTY_HIT_POINT[0],EMPTY_HIT_POINT[1],
                        EMPTY_HIT_POINT[2], false, isControllerActive, keyEvent, null);
            }
            previousHits.clear();
            previousHits.addAll(newHits);*/
            }
        }
    };

    private boolean recurseSceneObject(KeyEvent keyEvent, GVRSceneObject object,
                                       GVRBaseSensor sensor) {
        boolean sentEvent = false;
        GVRBaseSensor objectSensor = object.getSensor();
        if (objectSensor == null) {
            objectSensor = sensor;
        }

        if (objectSensor != null && objectSensor.isEnabled()) {
            if (object.hasMesh()) {
/*
                Matrix4f matrix4f = object.getTransform().getModelMatrix4f();

                float[] vertices = mesh.getVertices();
                int length = vertices.length;

                Vector3f cubeMin = new Vector3f(vertices[0], vertices[1], vertices[2]);
                Vector3f cubeMax = new Vector3f(vertices[length - 3], vertices[length - 2],
                        vertices[length - 1]);
                cubeMin.mulPosition(matrix4f);
                cubeMax.mulPosition(matrix4f);

                BoundingVolume volume = object.getBoundingVolume();
                Vector3f cubeMin = volume.minCorner;
                Vector3f cubeMax = volume.maxCorner;

//                cubeMin.mulPosition(matrix4f);
//                cubeMax.mulPosition(matrix4f);


                cubeMin.mulPosition(matrix4f);
                cubeMax.mulPosition(matrix4f);
                Vector3f objectPosition = new Vector3f(object.getTransform().getPositionX(),
                        object.getTransform().getPositionY(),object.getTransform().getPositionZ());
                objectPosition.mulPosition(matrix4f);
*/

                matrix4f.set(object.getTransform().getModelMatrix());
                GVRMesh mesh = object.getRenderData().getMesh().getBoundingBox();
                float[] vertices = mesh.getVertices();
                int length = vertices.length;
                cubeMin.set(vertices[0], vertices[1], vertices[2]);
                cubeMax.set(vertices[length - 3], vertices[length - 2], vertices[length - 1]);
                cubeMin.mulPosition(matrix4f);
                cubeMax.mulPosition(matrix4f);
                objectPosition.set(object.getTransform().getPositionX(),
                        object.getTransform().getPositionY(), object.getTransform().getPositionZ());
                objectPosition.mulPosition(matrix4f);


                SelectableGroup selectableBehavior = (SelectableGroup) object.getComponent
                        (SelectableGroup.getComponentType());

                GVRSceneObject sobject = object;
                if (selectableBehavior != null) {
                    sobject = selectableBehavior.getParent();
                }

                if (!pointInBox(cursorSceneObject.getPositionX(), cursorSceneObject.getPositionY(),
                        cursorSceneObject.getPositionZ(), cubeMin, cubeMax)) {
                    if (isColliding(object)) {

                        addNewHit(object);

/*
                        createAndSendCursorEvent(object, true, EMPTY_HIT_POINT[0],
                                EMPTY_HIT_POINT[1], EMPTY_HIT_POINT[2], true, isControllerActive,
                                keyEvent, null);
                        sentEvent = true;*/

//                        float[] hitpoint = new float[3];
//                        hitpoint[0] = object.getTransform().getPositionX() - cursorSceneObject
//                                .getPositionX();
//                        hitpoint[1] = object.getTransform().getPositionY() - cursorSceneObject
//                                .getPositionY();
//                        hitpoint[2] = object.getTransform().getPositionZ() - cursorSceneObject
//                                .getPositionZ();
//                        createAndSendCursorEvent(object, true, hitpoint, true, active, keyEvent);


                        float[] hitpoint = new float[3];
                        hitpoint[0] = cursorSceneObject.getPositionX() - objectPosition.x;
                        hitpoint[1] = cursorSceneObject.getPositionY() - objectPosition.y;
                        hitpoint[2] = cursorSceneObject.getPositionZ() - objectPosition.z;
                        //createAndSendCursorEvent(object, true, hitpoint, true, active, keyEvent);


                        addNewHit(sobject);
                        hitpoint[0] = cursorSceneObject.getPositionX() - objectPosition.x;
                        hitpoint[1] = cursorSceneObject.getPositionY() - objectPosition.y;
                        hitpoint[2] = cursorSceneObject.getPositionZ() - objectPosition.z;
          /*              createAndSendCursorEvent(sobject, true,
                                hitpoint, true, active, keyEvent);*/

                    }
                } else {
                    addNewHit(sobject);
                }
            }
        }

        for (GVRSceneObject child : object.getChildren()) {
            sentEvent = sentEvent || recurseSceneObject(keyEvent, child, objectSensor);
        }
        return sentEvent;
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


    void setScale(float scale) {
        // place the cursor at half the depth scale
        if(scale > MAX_CURSOR_SCALE) {
            return;
        }
        //super.setScale(scale / 2);

        if (ioDevice != null) {
            ioDevice.setNearDepth(POINT_CURSOR_NEAR_DEPTH);
        }
    }


    void setIoDevice(IoDevice ioDevice) {
        //super.setIoDevice(ioDevice);
        ioDevice.setNearDepth(POINT_CURSOR_NEAR_DEPTH);
    }

    void destroyIoDevice(IoDevice ioDevice) {
       // super.destroyIoDevice(ioDevice);
        /*if (active) {
            active = false;
        }*/
    }
}
