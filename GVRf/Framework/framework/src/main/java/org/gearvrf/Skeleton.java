package org.gearvrf;

import org.gearvrf.animation.GVRPose;
import org.gearvrf.utility.Log;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Skeleton {

    private static final int BONE_LOCK_ROTATION = 1;// lock bone rotation
    private static final int BONE_ANIMATE = 4;  	// keyframe bone animation
    private static final int BONE_PHYSICS = 2;  	// use physics to compute bone motion

    private static final String TAG = Skeleton.class.getSimpleName();
    protected Map<GVRSceneObject, List<GVRBone>> boneMap;
    private GVRSceneObject rootSceneObject;
    private Pose pose;
    private final Matrix4f scratchGlobalInverse;
    private final Matrix4f finalMatrix;

    public Skeleton(GVRSceneObject sceneObject) {
        this.rootSceneObject = sceneObject;
        boneMap = new HashMap<GVRSceneObject, List<GVRBone>>();
        pose = new Pose(sceneObject);
        scratchGlobalInverse = new Matrix4f();
        finalMatrix = new Matrix4f();
    }

    public Pose getPose() {
        return pose;
    }

    /**
     * Examines the child engines for the skeleton looking for Transformers which match the
     * bones in the skeleton. Use this function instead of AttachBones if you have already created
     * the bone Transformer hierarchy and want to attach it to the skeleton.
     */
    public void findBones(final GVRPose savepose) {
        GVRSceneObject owner = rootSceneObject;

        /*if (owner == null)
        {
            return;
        }
        if (savepose != null)
        {
            savepose.copy(mPose);
        }*/
        GVRSceneObject.SceneVisitor visitor = new GVRSceneObject.SceneVisitor() {
            @Override
            public boolean visit(GVRSceneObject newbone) {

                setupBone(newbone);
                pose.visit(newbone);
                return true;
            }
        };
        Log.d("rahul", "Owner children are " + owner.getChildrenCount());
        owner.forAllDescendants(visitor);
    }

    protected void setupBone(GVRSceneObject node) {
        GVRMesh mesh;
        if (node.getRenderData() != null && (mesh = node.getRenderData().getMesh()) != null) {
            Log.v(TAG, "setupBone checking mesh with %d vertices", mesh.getVertices().length / 3);
            for (GVRBone bone : mesh.getBones()) {
                bone.setSceneObject(node);

                GVRSceneObject skeletalNode = rootSceneObject.getSceneObjectByName(bone.getName());
                if (skeletalNode == null) {
                    Log.w(TAG, "what? cannot find the skeletal node for bone: %s", bone.toString());
                    continue;
                }

                // Create look-up table for bones
                List<GVRBone> boneList = boneMap.get(skeletalNode);
                if (boneList == null) {
                    boneList = new ArrayList<GVRBone>();
                    boneMap.put(skeletalNode, boneList);
                }
                boneList.add(bone);
            }
        }
    }

    /**
     * Use this call to update all the bone matrices once the new transforms have been set.
     *
     * Future: Could do this during onDrawFrame()
     */
    public void update() {
        //this is a pose function
        //updateTransforms(rootSceneNode, new Matrix4f());
        pose.update();
        // this is a skeleton function
        for (Map.Entry<GVRSceneObject, List<GVRBone>> ent : boneMap.entrySet()) {
            // Transform all bone splits (a bone can be split into multiple instances if they
            // influence different meshes)
            //Pose.SceneNode node = nodeByName.get(ent.getKey().getName());
            for (GVRBone bone : ent.getValue()) {
                updateBoneMatrices(bone, ent.getKey());
            }
        }
    }

    protected void updateBoneMatrices(GVRBone bone, GVRSceneObject node) {
        finalMatrix.set(bone.getOffsetMatrixFloatArray());

        Matrix4f globalTransform = pose.getGlobalMatrix(node.getName());

        globalTransform.mul(finalMatrix, finalMatrix);



        scratchGlobalInverse.set(bone.getSceneObject().getTransform().getModelMatrix());
        scratchGlobalInverse.invert();
        scratchGlobalInverse.mul(finalMatrix, finalMatrix);

        bone.setFinalTransformMatrix(finalMatrix);
    }
}
