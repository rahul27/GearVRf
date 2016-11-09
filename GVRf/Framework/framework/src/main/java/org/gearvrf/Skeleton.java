package org.gearvrf;

import org.gearvrf.animation.GVRPose;
import org.gearvrf.animation.GVRSkeleton;
import org.gearvrf.utility.Log;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Skeleton {

    public static final int BONE_LOCK_ROTATION = 1;// lock bone rotation
    public static final int BONE_ANIMATE = 4;    // keyframe bone animation
    public static final int BONE_PHYSICS = 2;    // use physics to compute bone motion

    private static final String TAG = Skeleton.class.getSimpleName();
    protected Map<GVRSceneObject, List<GVRBone>> boneMap;
    private GVRSceneObject rootSceneObject;
    private Pose pose;
    private final Matrix4f scratchGlobalInverse;
    private final Matrix4f finalMatrix;
    List<GVRBone> bones;
    List<GVRBone> orderedBones;
    protected int[] mBoneOptions;
    Map<String, Integer> boneIndexMap;

    public Skeleton(GVRSceneObject sceneObject) {
        this.rootSceneObject = sceneObject;
        boneMap = new HashMap<GVRSceneObject, List<GVRBone>>();
        pose = new Pose(this, sceneObject);
        scratchGlobalInverse = new Matrix4f();
        finalMatrix = new Matrix4f();
        bones = new ArrayList<GVRBone>();
        orderedBones = new ArrayList<GVRBone>();
        boneIndexMap = new HashMap<String, Integer>();
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

        int mNumBones = bones.size();
        mBoneOptions = new int[mNumBones];
        pose.updateBoneIndices();

        boneIndexMap.clear();
        int index =0;
        for(GVRBone bone :orderedBones){
            boneIndexMap.put(bone.getName(), index);
            index++;
        }
        // update once, to generate matrices
        pose.update();
    }
    int boneIndex = 0;
    protected void setupBone(GVRSceneObject node) {
        GVRMesh mesh;
        if (node.getRenderData() != null && (mesh = node.getRenderData().getMesh()) != null) {
            Log.v(TAG, "setupBone checking mesh with %d vertices", mesh.getVertices().length / 3);
            for (GVRBone bone : mesh.getBones()) {
                bone.setSceneObject(node);

                // TODO verify if the list is correctly ordered
                bones.add(bone);
                boneIndexMap.put(bone.getName(), boneIndex);
                Log.d("rahul", "Bone %s is %d", bone.getName(), boneIndex);
                boneIndex++;

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
         /*
     * Update the pose based on bone animations.
     * If any of the bones are animated, they will be marked as changed.
     */

        //Log.d("rahul", "BL is %d OBL is %s",bones.size(), orderedBones.size());
        Quaternionf q = new Quaternionf();
        Vector3f p = new Vector3f();
        for (int i = 0; i < orderedBones.size(); ++i) {
            GVRBone bone = orderedBones.get(i);
            if (bone == null)
                continue;
            GVRTransform trans = pose.getSceneObject(bone).getTransform();
            if ((mBoneOptions[i] & BONE_ANIMATE) != 0) {

                //pose.updateLocalMatrix(bone, trans.getModelMatrix4f());
                Matrix4f matrix4f = trans.getLocalModelMatrix4f();

                //GVRTransform trans = sceneObject.getTransform();
                q.set(trans.getRotationX(), trans.getRotationY(), trans.getRotationZ(), trans
                        .getRotationW());
                //Vector3f p = new Vector3f();
                //q.normalize();

                // pose.updateLocalMatrix(bones.get(i), newMatrix4f);
                pose.updateLocalRotation(orderedBones.get(i).getName(), q);
                //q.set(trans.getRotationX(), trans.getRotationY(), trans.getRotationZ(), trans
                //    .getRotationW());
                //q.normalize();
                //pose.updateGlobalRotation(bones.get(i).getName(), q);
                //TO add this
                /*if (i == 0)
                {
                    Matrix4f matrix4f = trans.getModelMatrix4f();

                    p.set(trans.getPositionX(), trans.getPositionY(), trans.getPositionZ());
                    matrix4f.transformPoint(p,p);
                //    p.add(mRootOffset);
                   // pose.updateLocalPosition(bones.get(i).getName(), p);

                }else{

                }*/
            }
        }
        //pose.update(orderedBones.get(0).getName());
        pose.update();
        // this is a skeleton function
        for (int i = 0; i < bones.size(); ++i) {
            GVRBone bone = bones.get(i);
            updateBoneMatrices(bone);
        }
    }

    /**
     * Set rotation and physics options for this bone
     *
     * @param boneindex 0 based bone index
     * @param options   options to control how this bone moves
     *                  BONE_PHYSICS will use physics (rigid body dynamics) to calculate
     *                  the motion of the bone
     *                  BONE_LOCK_ROTATION will lock the bone rotation, freezing its current
     *                  local rotation
     * @see GVRSkeleton.setLocalRotation GVRSkeleton.setWorldRotation
     */
    public void setBoneOptions(int boneindex, int options) {
        mBoneOptions[boneindex] = options;
    }

    /**
     * Get rotation and physics options for this bone
     *
     * @param boneindex 0 based bone index
     * @see GVRSkeleton.setBoneOptions
     */
    public int getBoneOptions(int boneindex) {
        return mBoneOptions[boneindex];
    }

    /**
     * Get rotation and physics options for this bone
     *
     * @param boneindex 0 based bone index
     * @see GVRSkeleton.setBoneOptions
     */
    public int getBoneOptions(GVRSceneObject sceneObject) {
        Integer index = boneIndexMap.get(sceneObject.getName());
        return mBoneOptions[index];
    }

    /**
     * Set rotation and physics options for this bone
     *
     * @param boneindex 0 based bone index
     * @param options   options to control how this bone moves
     *                  BONE_PHYSICS will use physics (rigid body dynamics) to calculate
     *                  the motion of the bone
     *                  BONE_LOCK_ROTATION will lock the bone rotation, freezing its current
     *                  local rotation
     * @see GVRSkeleton.setLocalRotation GVRSkeleton.setWorldRotation
     */
    public void setBoneOptions(GVRSceneObject sceneObject, int options) {
        Integer index = boneIndexMap.get(sceneObject.getName());

        if (index == null) {
            Log.d("rahul", "No bone found for " + sceneObject.getName());
            return;
        }
        mBoneOptions[index] = options;
    }

    protected void updateBoneMatrices(GVRBone bone) {
        finalMatrix.set(bone.getOffsetMatrixFloatArray());

        Matrix4f globalTransform = pose.getGlobalMatrix(bone.getName());

        globalTransform.mul(finalMatrix, finalMatrix);

        scratchGlobalInverse.set(bone.getSceneObject().getTransform().getModelMatrix());
        scratchGlobalInverse.invert();
        scratchGlobalInverse.mul(finalMatrix, finalMatrix);

        bone.setFinalTransformMatrix(finalMatrix);
    }
}
