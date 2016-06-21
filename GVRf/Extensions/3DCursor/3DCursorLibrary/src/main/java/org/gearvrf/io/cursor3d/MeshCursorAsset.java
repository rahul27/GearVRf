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

import android.util.SparseArray;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRMaterial;
import org.gearvrf.GVRMaterial.GVRShaderType.Texture;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRRenderData;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRTexture;
import org.gearvrf.utility.Log;

import java.io.IOException;
import java.util.concurrent.Future;

/**
 * This class allows a mesh and a texture to be set on a {@link Cursor object}.
 */
class MeshCursorAsset extends CursorAsset {
    private static final String TAG = MeshCursorAsset.class.getSimpleName();
    private static final int OVERLAY_RENDER_ORDER = 100000;
    private Future<GVRTexture> futureTexture;
    private GVRMesh mesh;
    private Future<GVRMesh> futureMesh;
    private String texture;
    private float x;
    private float y;
    //protected SparseArray<GVRRenderData> renderDataArray;
    protected SparseArray<GVRSceneObject> renderDataArray;

    MeshCursorAsset(GVRContext context, CursorType type, Action action, String texture) {
        this(context, type, action, null, texture);
    }

    MeshCursorAsset(GVRContext context, CursorType type, Action action, String mesh, String
            texture) {
        super(context, type, action);
        renderDataArray = new SparseArray<GVRSceneObject>();

        if (mesh != null) {
            try {
                futureMesh = context.loadFutureMesh(new GVRAndroidResource(context, mesh));
            } catch (IOException e) {
                throw new IllegalArgumentException("Error loading mesh");
            }
        }
        if(texture != null) {
            try {
                context.getContext().getAssets().open(texture).close();
            } catch (IOException e) {
                throw new IllegalArgumentException("Error loading Texture");
            }
            this.texture = texture;
        }
    }

    /**
     * Set the texture value for the behavior
     *
     * @param texture the {@link Future<GVRTexture>} to associate with the behavior
     */

    void setTexture(Future<GVRTexture> texture) {
        this.futureTexture = texture;
    }

    void setQuadMesh(float x, float y) {
        this.x = x;
        this.y = y;
        mesh = context.createQuad(x, y);
    }

    @Override
    void load(CursorSceneObject sceneObject) {
        // try to get a cached copy from GVRf
        if (futureTexture == null && texture != null) {
            try {
                GVRAndroidResource gvrAndroidResource = new GVRAndroidResource(context, texture);
                futureTexture = context.loadFutureTexture(gvrAndroidResource);
            } catch (IOException e) {
                Log.e(TAG, "Error loading texture", e);
            }
        }
        int key = sceneObject.getId();
        GVRSceneObject child = renderDataArray.get(key);

        if (child == null) {
            child = new GVRSceneObject(context);
            GVRRenderData renderData = new GVRRenderData(context);
            renderData.setMaterial(new GVRMaterial(context, Texture.ID));

            if (cursorType == CursorType.LASER) {
                renderData.setDepthTest(false);
                renderData.setRenderingOrder(OVERLAY_RENDER_ORDER);
            }
            child.attachRenderData(renderData);
            renderDataArray.append(key, child);
        }
        GVRRenderData renderData = child.getRenderData();
        if (mesh != null) {
            renderData.setMesh(mesh);
        } else if (futureMesh != null) {
            renderData.setMesh(futureMesh);
        }

        if (futureTexture != null) {
            renderData.getMaterial().setMainTexture(futureTexture);
        }
        child.setEnable(false);
        sceneObject.addChildObject(child);
    }

    @Override
    void unload(CursorSceneObject sceneObject) {
        //clear the reference to the texture
        GVRSceneObject sceneObject1 = renderDataArray.get(sceneObject.getId());

        //check if there are cursors still using the texture
        if (renderDataArray.size() == 1) {
            futureTexture = null;
        }

        sceneObject.removeChildObject(sceneObject1);
        renderDataArray.remove(sceneObject.getId());

    }

    void set(final CursorSceneObject sceneObject) {
        super.set(sceneObject);
        final GVRSceneObject renderData = renderDataArray.get(sceneObject.getId());
        if (renderData == null) {
            Log.e(TAG, "Render data not found, should not happen");
            return;
        }
        sceneObject.set(renderData);
        renderData.setEnable(true);

        // this call can only be made on the GL Thread
        /*context.runOnGlThread(new Runnable() {
            @Override
            public void run() {
                sceneObject.attachRenderData(renderData);
            }
        });*/

    }

    /**
     * Use the reset method to remove this asset from the given {@link GVRSceneObject}.
     *
     * @param sceneObject the {@link GVRSceneObject}  for the behavior to be removed
     */

    void reset(final CursorSceneObject sceneObject) {
        super.reset(sceneObject);
        final GVRSceneObject renderData = renderDataArray.get(sceneObject.getId());
        sceneObject.reset();
        renderData.setEnable(false);

        //sceneObject.removeChildObject(renderData);
        // this call can only be made on the GL Thread
        /*context.runOnGlThread(new Runnable() {
            @Override
            public void run() {
                sceneObject.detachRenderData();
            }
        });*/

    }

    float getX() {
        return x;
    }

    float getY() {
        return y;
    }
}
