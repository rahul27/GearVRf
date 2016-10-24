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
package com.sample.hand.template;

import org.gearvrf.GVRSceneObject;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * This class represents a base object that manages a scene object with methods that can set its
 * type,  position and   rotation .
 */
public abstract class IOBaseComponent {
    public GVRSceneObject sceneObject;
    private int type;
    private GVRSceneObject parent;
    private Vector3f componentPosition;
    private Quaternionf componentRotation;
    private Vector3f scratchVector;

    /**
     * Create an {@link IOBaseComponent} of the provided type.
     *
     * @param type            the type of the {@link IOBaseComponent}.
     * @param parent This is the root {@link GVRSceneObject} that represents the hand.
     */
    public IOBaseComponent(int type, GVRSceneObject parent) {
        this.type = type;
        this.parent = parent;
        componentPosition = new Vector3f();
        componentRotation = new Quaternionf();
        scratchVector = new Vector3f();
    }

    /**
     * Get the scene object that represents this component.
     *
     * @return This call returns null if no scene object has been set
     */
    public GVRSceneObject getSceneObject() {
        return sceneObject;
    }

    /**
     * Set the parent of this component
     * @param sceneObject
     */
    public void setParent(GVRSceneObject sceneObject) {
        this.parent = sceneObject;
    }

    /**
     * This call sets the {@link GVRSceneObject} that represents this {@link IOBaseComponent} and
     * adds it to the root hand object.
     *
     * @param sceneObject
     */
    public void setSceneObject(GVRSceneObject sceneObject) {
        this.sceneObject = sceneObject;
        parent.addChildObject(sceneObject);
    }

    /**
     * Set the rotation for the {@link IOBaseComponent}
     *
     * @param w the w value of the quaternion
     * @param x the x value of the quaternion
     * @param y the y value of the quaternion
     * @param z the z value of the quaternion
     */
    public void setRotation(float w, float x, float y, float z) {
        componentRotation.set(w, x, y, z);
        if (sceneObject != null) {
            sceneObject.getTransform().setRotation(w, x, y, z);
        }
    }

    /**
     * Set the rotation for the {@link IOBaseComponent}
     *
     * @param rotation the quaternion that represents the bone rotation
     */
    public void setRotation(Quaternionf rotation) {
        setRotation(rotation.w, rotation.x, rotation.y,
                rotation.z);
    }

    /**
     * Set the position of the {@link IOBaseComponent}
     *
     * @param x the x value of the quaternion
     * @param y the y value of the quaternion
     * @param z the z value of the quaternion
     */
    public void setPosition(float x, float y, float z) {
        componentPosition.set(x, y, z);
        if (sceneObject != null) {
            sceneObject.getTransform().setPosition(x, y, z);
        }
    }

    /**
     * Set the position of the {@link IOBaseComponent}
     * param position the vector representing the position
     */
    public void setPosition(Vector3f position) {
        setPosition(position.x, position.y, position.z);
    }

    /**
     * Get the type of this {@link IOBaseComponent}
     *
     * @return the type of this {@link IOBaseComponent} as an int
     */
    public int getType() {
        return type;
    }

    /**
     * Get the position of the {@link IOBaseComponent}.
     *
     * @return the position as a vector
     */
    public Vector3f getPosition() {
        if (sceneObject != null) {
            // use the hierarchical position
            Matrix4f matrix4f = sceneObject.getTransform().getModelMatrix4f();
            scratchVector.zero();
            matrix4f.transformPoint(scratchVector, scratchVector);
            return scratchVector;
        }else{
            return  componentPosition;
        }

    }

    /**
     * Get the rotation of this {@link IOBaseComponent}.
     *
     * @return the rotation as a quaternion.
     */
    public Quaternionf getRotation() {
        return componentRotation;
    }
}
