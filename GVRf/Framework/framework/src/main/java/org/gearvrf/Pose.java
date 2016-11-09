package org.gearvrf;

import org.gearvrf.utility.Log;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Use this class to control the skeletal structure of
 * a loaded model.
 */
public class Pose {
    private static final String TAG = Pose.class.getSimpleName();
    private static int UPDATED_GLOBAL_POSITION = 1 << 0;
    private static int UPDATED_GLOBAL_ROTATION = 1 << 1;
    private static int UPDATED_LOCAL_TRANSFORM = 1 << 2;
    protected GVRContext gvrContext;
    protected GVRSceneObject rootSceneObject;
    //protected SceneNode rootSceneNode;
    private Map<String, SceneNode> nodeByName;
    //protected Map<GVRSceneObject, List<GVRBone>> boneMap;

    // temp objects to reduce GC cycles
    private final Vector3f scratchRotationVector;
    private Skeleton skeleton;


    private class SceneNode {
        private int updateStatus;
        private GVRSceneObject sceneObject;
        private SceneNode parent;
        private List<SceneNode> children;

        // following matrices reduce load on GC
        private Matrix4f localTransform;
        private Matrix4f globalTransform;
        private boolean isValid;
        private Vector3f globalPosition;
        private Quaternionf globalRotation;
        private Vector3f localPosition;
        private Quaternionf localRotation;

        SceneNode(GVRSceneObject sceneObject, SceneNode parent) {
            this.sceneObject = sceneObject;
            this.parent = parent;
            children = new ArrayList<SceneNode>();
            localTransform = new Matrix4f();
            globalTransform = new Matrix4f();
            isValid = true;
            globalPosition = new Vector3f();
            globalRotation = new Quaternionf();
            localPosition = new Vector3f();
            localRotation = new Quaternionf();
        }

        void setIsValid(boolean isValid) {
            this.isValid = isValid;
        }
    }

    /**
     * Constructs the skeletal controller for the provided {@link GVRSceneObject}
     *
     * @param sceneObject The corresponding scene object.
     */
    public Pose(Skeleton skeleton, GVRSceneObject sceneObject) {
        this.skeleton = skeleton;
        this.rootSceneObject = sceneObject;
        nodeByName = new TreeMap<String, SceneNode>();
        //boneMap = new HashMap<GVRSceneObject, List<GVRBone>>();
        scratchRotationVector = new Vector3f();

        //this.rootSceneNode = createTree(sceneObject, null);
        visit(sceneObject);

        //update once

        //this.rootSceneNode  = nodeByName.get(sceneObject.getName());
    }





    void visit(GVRSceneObject node) {
        GVRSceneObject parentNode = node.getParent();
        SceneNode parent = null;

        if(parentNode != null){
            parent = nodeByName.get(parentNode.getName());

        }
        Log.v("rahul", "Visit %s Parent %s", node.getName() ,  (node.getParent() != null?node
                .getParent()
                .getName():null));
        SceneNode internalNode = new SceneNode(node, parent);
        nodeByName.put(node.getName(), internalNode);

        // Bind-pose local transform
        internalNode.localTransform.set(node.getTransform().getLocalModelMatrix4f());
        internalNode.globalTransform.set(internalNode.localTransform);

        // Global transform
        if (parent != null) {
            parent.globalTransform.mul(internalNode.globalTransform, internalNode.globalTransform);
            parent.children.add(internalNode);


        }






    }


    public void updateBoneIndices(){
        SceneNode rootSceneNode = nodeByName.get(rootSceneObject.getName());
        updateBoneIndices(rootSceneNode);

    }

    public void updateBoneIndices(SceneNode node){
        Integer boneIndex = skeleton.boneIndexMap.get(node.sceneObject.getName());

        if (boneIndex != null){
            GVRBone bone = skeleton.bones.get(boneIndex);
            skeleton.orderedBones.add(bone);
        }

        for (SceneNode child : node.children) {
            updateBoneIndices(child);
        }

    }

    /**
     * Use this call to update all the bone matrices once the new transforms have been set.
     *
     * Future: Could do this during onDrawFrame()
     */
    public void update() {
        //this is a pose function
        SceneNode rootSceneNode = nodeByName.get(rootSceneObject.getName());
        updateTransforms(rootSceneNode, new Matrix4f());

    }

    /**
     * Use this call to update all the bone matrices once the new transforms have been set.
     *
     * Future: Could do this during onDrawFrame()
     */
    public void update(String root) {
        //this is a pose function
        SceneNode rootSceneNode = nodeByName.get(root);
        updateTransforms(rootSceneNode, rootSceneNode.sceneObject.getParent().getTransform()
                .getModelMatrix4f());

    }


    public GVRSceneObject getSceneObject(GVRBone bone){
        SceneNode node = nodeByName.get(bone.getName());
        if(node != null){
            return node.sceneObject;
        }

        return null;
    }



    /**
     * Update the global/world position of the provided bone.
     *
     * @param bone     the corresponding {@link GVRBone} object
     * @param position the new position in world coordinates.
     */
    public void updateGlobalPosition(GVRBone bone, Vector3f position) {
        updateGlobalPosition(bone.getName(), position);
    }

    /**
     * Update the global/world rotation of the provided bone.
     *
     * @param bone     the corresponding {@link GVRBone} object
     * @param rotation the new rotation in world coordinates.
     */
    public void updateGlobalRotation(GVRBone bone, Quaternionf rotation) {
        updateGlobalRotation(bone.getName(), rotation);
    }

    /**
     * Update the global/world position of the provided bone.
     *
     * @param boneName the name of the corresponding {@link GVRBone} object
     * @param position the new position in world coordinates.
     */
    public void updateGlobalPosition(String boneName, Vector3f position) {
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException("Bone not found");
        }
        node.updateStatus = node.updateStatus | UPDATED_GLOBAL_POSITION;
        node.globalPosition.set(position);
    }

    /**
     * Update the global/world rotation of the provided bone.
     *
     * @param boneName the name of the corresponding {@link GVRBone} object
     * @param rotation the new rotation in world coordinates.
     */
    public void updateGlobalRotation(String boneName, Quaternionf rotation) {
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException("Bone not found");
        }
        node.updateStatus = node.updateStatus | UPDATED_GLOBAL_ROTATION;
        node.globalRotation.set(rotation);
    }

    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param sceneObject the corresponding {@link GVRSceneObject}
     * @param matrix4f    the new local transform matrix
     */
    public void updateLocalMatrix(GVRSceneObject sceneObject, Matrix4f matrix4f) {
        SceneNode node = nodeByName.get(sceneObject.getName());
        node.updateStatus = node.updateStatus | UPDATED_LOCAL_TRANSFORM;
        node.localTransform.set(matrix4f);
    }


    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param sceneObject the corresponding {@link GVRSceneObject}
     * @param rotation    the new local transform matrix
     */
    public void updateLocalRotation(GVRSceneObject sceneObject, Quaternionf rotation) {
        updateLocalRotation(sceneObject.getName(), rotation);
    }

    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param boneName the name of the corresponding {@link GVRBone} object
     * @param rotation    the new local transform matrix
     */
    /*public void updateLocalRotation(String boneName, Quaternionf rotation) {
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException(String.format("Bone %s not found", boneName));
        }
        node.updateStatus = node.updateStatus | UPDATED_LOCAL_TRANSFORM;
        rotation.getEulerAnglesXYZ(scratchRotationVector);
        node.localTransform.setRotationXYZ(scratchRotationVector.x, scratchRotationVector.y,
                scratchRotationVector.z);
    }*/


    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param boneName the name of the corresponding {@link GVRBone} object
     * @param rotation    the new local transform matrix
     */
    public void updateLocalRotation(String boneName, Quaternionf rotation) {
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException(String.format("Bone %s not found", boneName));
        }
        node.updateStatus = node.updateStatus | UPDATED_LOCAL_TRANSFORM;

        node.localTransform.identity();
        node.localTransform.rotate(rotation);
        node.localTransform.setTranslation(node.localPosition);

        //rotation.getEulerAnglesXYZ(scratchRotationVector);
        //node.localTransform.setRotationXYZ(scratchRotationVector.x, scratchRotationVector.y,
         //       scratchRotationVector.z);
    }

    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param boneName the name of the corresponding {@link GVRBone} object
     * @param rotation    the new local transform matrix
     */
    public void updateLocalPosition(String boneName, Vector3f position) {
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException(String.format("Bone %s not found", boneName));
        }
        node.updateStatus = node.updateStatus | UPDATED_LOCAL_TRANSFORM;

        node.localTransform.setTranslation(position);
    }

    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param bone     the corresponding {@link GVRSceneObject}
     * @param matrix4f the new local transform matrix
     */
    public void updateLocalMatrix(GVRBone bone, Matrix4f matrix4f) {
        updateLocalMatrix(bone.getName(), matrix4f);
    }


    /**
     * Update the local matrix transform of the provided {@link GVRSceneObject}
     *
     * @param boneName the name of the corresponding {@link GVRBone} object
     * @param matrix4f the new local transform matrix
     */
    public void updateLocalMatrix(String boneName, Matrix4f matrix4f) {
        SceneNode node = nodeByName.get(boneName);

        node.updateStatus = node.updateStatus | UPDATED_LOCAL_TRANSFORM;
        node.localTransform.set(matrix4f);
    }

    public Matrix4f getLocalMatrix(String boneName){
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException(String.format("Bone %s not found", boneName));
        }
        return node.localTransform;
    }

    public Matrix4f getGlobalMatrix(String boneName){
        SceneNode node = nodeByName.get(boneName);
        if(node == null){
            throw new IllegalArgumentException(String.format("Bone %s not found", boneName));
        }
        return node.globalTransform;
    }
    //boolean flag = true;
    public SceneNode getSceneNode(GVRBone bone) {
        return nodeByName.get(bone.getName());
    }

    protected void updateTransforms(SceneNode node, Matrix4f parentTransform) {

        if ((node.updateStatus & UPDATED_LOCAL_TRANSFORM) != 0) {
            //do nothing
        } else {
            node.localTransform.set(node.sceneObject.getTransform().getLocalModelMatrix4f());
            node.localTransform.getUnnormalizedRotation(node.localRotation);
            node.localTransform.getTranslation(node.localPosition);
        }


        //Log.d("rahul", "Process " + node.sceneObject.getName()+ " " + node.children.size());

        parentTransform.mul(node.localTransform, node.globalTransform);

        if ((node.updateStatus & UPDATED_GLOBAL_ROTATION) != 0) {
            node.globalRotation.getEulerAnglesXYZ(scratchRotationVector);
            node.globalTransform.setRotationXYZ(scratchRotationVector.x, scratchRotationVector.y,
                    scratchRotationVector.z);
        }

        if ((node.updateStatus & UPDATED_GLOBAL_POSITION) != 0) {
            node.globalTransform.setTranslation(node.globalPosition.x, node.globalPosition.y,
                    node.globalPosition.z);
        }

        // reset the update status
        node.updateStatus = 0;

        for (SceneNode child : node.children) {
            updateTransforms(child, node.globalTransform);
        }
    }



    /**
     * Prune the tree to remove nodes that may be considered invalid using the
     * {@link #setInvalid(GVRSceneObject)} call.
     */
    public void pruneTree() {
        SceneNode rootSceneNode = nodeByName.get(rootSceneObject.getName());
        pruneTree(rootSceneNode);
    }

    /**
     * This is a part of the skeletal controller optimization.
     *
     * Select parts of the model hierarchy as invalid to prune the
     * tree of nodes that do not impact the skeletal transform.
     *
     * Nodes that are marked as invalid using this call will be <b>considered</b> for pruning
     * when the call to {@link #pruneTree()} is made.
     *
     * @param sceneObject the {@link GVRSceneObject} to be marked invalid.
     */
    public void setInvalid(GVRSceneObject sceneObject) {
        SceneNode node = nodeByName.get(sceneObject.getName());
        node.setIsValid(false);
    }

    /* Returns true if the subtree should be kept */
    protected boolean pruneTree(SceneNode node) {
        boolean keep = node.isValid;

        if (keep) {
            return keep;
        }

        Iterator<SceneNode> iter = node.children.iterator();
        while (iter.hasNext()) {
            SceneNode child = iter.next();

            boolean keepChild = pruneTree(child);
            keep |= keepChild;
            if (!keepChild) {
                iter.remove();
            }
        }

        return keep;
    }
}