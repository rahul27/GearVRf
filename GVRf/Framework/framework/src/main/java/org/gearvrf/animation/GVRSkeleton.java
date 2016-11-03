package org.gearvrf.animation;

import org.gearvrf.GVRBehavior;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRTransform;
import org.gearvrf.utility.Log;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Component that animates a skeleton based on a set of bones.
 *
 * This class provides a common infrastructure for skeletal animation.
 * It can construct an animation hierarchy representing the bones of a skeleton
 * which can be used to animate a skinned character. You can set the bone names in the skeleton
 * to designate which shapes in your hierarchy represent which body parts.
 * If you set a hierarchy of shapes to be the target of the Skeleton,
 * it will construct an animation hierarchy for you. The asset loader also
 * constructs an animation hierarchy when you export a rigged character.
 *
 * The Skeleton relies on the Pose class to represent the position and orientation of its bones.
 * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
 * The pose orients and positions each bone in the skeleton respect to this initial state by maintaining
 * a matrix for each bone. The root bone which is the parent of all the other bones in the
 * hierarchy should be the first bone (with index 0).
 *
 * The "bind pose" of the skeleton defines the neutral position of the bones before any
 * external transformations are applied. Usually it represents the pose of the skeleton
 * matching the meshes which will be skinned from the skeleton. The bone transformations
 * used for skinning the mesh are relative to the bind pose.
 *
 * The "current pose" of the skeleton defines the current orientation and position of the bones.
 * It uses the same conventions as the bind pose, relative to bones at the origin along the bone axis
 * (It is NOT relative to the bind pose). When the current pose is updated, the skeleton modifies the matrices
 * attached to the models in the hierarchy and the bone matrices for each child Skin. The current pose
 * can be shared with other engines.
 */
public class GVRSkeleton extends GVRBehavior
{
    private static final int BONE_LOCK_ROTATION = 1;// lock bone rotation
    private static final int BONE_ANIMATE = 4;  	// keyframe bone animation
    private static final int BONE_PHYSICS = 2;  	// use physics to compute bone motion

    private static final String TAG = Log.tag(GVRSkeleton.class);
    static private long TYPE_SKELETON = newComponentType(GVRSkeleton.class);
    final protected List<GVRSceneObject>    mBones;
    final protected int[]       mParentBones;
    final protected int[]       mBoneOptions;
    final protected String[]    mBoneNames;
    final protected Matrix4f[]  mInverseBindPose;
    protected Vector3f	        mRootOffset;	// offset for root bone animations
    protected Vector3f	        mBoneAxis;		// axis of bone, defines bone coordinate system
    protected GVRPose           mSkinPose;		// current skinning pose (bind pose factored out)
    protected GVRPose           mBindPose;	    // bind pose for this skeleton
    protected GVRPose	        mPose;			// current pose for this skeleton

    static public long getComponentType() { return TYPE_SKELETON; }

    public GVRSkeleton(GVRContext ctx, int[] parentBones)
    {
        super(ctx);
        mType = getComponentType();
        mParentBones = parentBones;
        mBoneAxis = new Vector3f(0, 0, 1);
        mRootOffset = new Vector3f(0, 0, 0);
        mBoneOptions = new int[parentBones.length];
        mBoneNames = new String[parentBones.length];
        mInverseBindPose = new Matrix4f[parentBones.length];
        mBones = new ArrayList<GVRSceneObject>();
        mBindPose = new GVRPose(this, GVRPose.PoseSpace.SKELETON);
        mSkinPose = new GVRPose(this, GVRPose.PoseSpace.SKELETON);
    }

    public GVRSkeleton(GVRSkeleton src)
    {
        super(src.getGVRContext());
        mType = getComponentType();
        mParentBones = src.mParentBones;
        mBoneAxis = src.mBoneAxis;
        mRootOffset = src.mRootOffset;
        mBoneOptions = src.mBoneOptions;
        mBoneNames = src.mBoneNames;
        mInverseBindPose = src.mInverseBindPose;
        mBones = src.mBones;
        mBindPose = new GVRPose(this, GVRPose.PoseSpace.SKELETON);
        mSkinPose = new GVRPose(this, GVRPose.PoseSpace.SKELETON);
    }

    /**
     * Get world space position of skeleton (position of the root bone, index 0).
     *
     * The world space position of the skeleton is the translation of
     * the root bone (index 0) of the current pose.
     */
    public Vector3f	getPosition() { return mPose.getWorldPosition(0); }

    /**
     * Sets the world space position of the skeleton.
     * @param pos	world space position of the skeleton.
     *
     * The world space position of the skeleton is the translation of
     * the root bone (index 0) of the current pose. Setting the skeleton position
     * directly updates both the root bone position and the translation
     * on the target model of the skeleton.
     *
     * @see GVRPose.setWorldPositions
     */
    public void	    setPosition(Vector3f pos)
    {
        mPose.setPosition(pos);
    }

    /**
     * Gets the offset of the root bone when animated.
     *
     * This offset is useful for moving the starting point of animations.
     * For example, you can make a zero-based animation relative to the
     * current position of the character by setting this offset to that position.
     * By default, this offset is zero.
     *
     * @see GVRSkeleton.setRootOffset
     */
    public Vector3f getRootOffset() { return  mRootOffset; }

    /**
     * Sets the offset of the root bone when animated.
     * @param ofs	vector with new root bone offset
     *
     * This offset is useful for moving the starting point of animations.
     * For example, you can make a zero-based animation relative to the
     * current position of the character by setting this offset to that position.
     * By default, this offset is zero.
     *
     * The root bone offset only affects animations. It does not affect any
     * updates to the current pose.
     *
     * @see GVRSkeleton.getRootOffset
     */
    public void     setRootOffset(Vector3f ofs)
    {
        mRootOffset = ofs;
    }

    /**
     * Set bind pose - neutral positions and rotations for all the bones relative to skeleton root joint.
     * @param pose	Pose containing rotations and positions for the bind pose
     *
     * Replaces the bind pose of the skeleton with the input GVRPose object.
     * The "bind pose" of the skeleton defines the neutral position of the bones before any
     * animations are applied. Usually it represents the pose that matches
     * the source vertices of the Skins driven by the skeleton.
     * You can restore the skeleton to it's bind pose with RestoreBindPose().
     *
     * The bind pose is maintained as a Pose object internal to the skeleton
     * and connot be shared across skeletons. Setting the bind pose copies the
     * value of the input pose into the skeleton's bind pose. Subsequent
     * updates to the input pose are not reflected in the skeleton's bind pose.
     * These semantics are different than elsewhere in the API.
     *
     * @see GVRSkeleton.setPose GVRSkeleton.getBindPose GVRSkeleton.restoreBindPose GVRPose
     */
    public void	     setBindPose(GVRPose pose)
    {
        mBindPose.copy(pose);
        updateBindPose();
    }

    /**
     * Updates the rotations and positions of the existing bind pose of this skeleton.
     * @param rotations	new bind pose rotations
     * @param positions new bind pose positions
     *
     * Both arrays must have enough entries for all the bones in the skeleton.
     * The bind pose" of the skeleton defines the neutral position of the bones before any
     * animations are applied. Usually it represents the pose that matches
     * the source vertices of the Skins driven by the skeleton.
     * You can restore the skeleton to it's bind pose with RestoreBindPose().
     *
     * @see GVRSkeleton.setPose GVRSkeleton.getBindPose GVRSkeleton.restoreBindPose GVRPose
     */
    public void	     setBindPose(Quaternionf[] rotations, Vector3f[] positions)
    {
        mBindPose.setWorldRotations(rotations);
        mBindPose.setWorldPositions(positions);
        mBindPose.sync();
        updateBindPose();
    }

    /**
     * The bind pose is the pose the skeleton is in when no rotations are
     * applied to it's joints. This is a reference to an internal Pose object
     * which cannot be shared across multiple Skin objects.
     *
     * @see GVRSkeleton.getNumBones GVRSkeleton.setBindPose Pose.getPose Pose.setWorldRotations Pose.setWorldPositions
     */
    public GVRPose   getBindPose() { return mBindPose; }

    /**
     * Get the current skeleton pose.
     * The current pose is the pose the skeleton is currently in. It contains the
     * location and orientation of each bone relative to the skeleton root bone.
     * The current pose is a reference to an internal Pose object that changes dynamically.
     * Modifications made to this Pose will affect the skeleton. Unlike the bind pose, which is referenced
     * when a Skeleton object is copied or cloned, the current pose is duplicated. Multiple skeletons
     * can share the same current pose if you use Skeleton::SetPose.
     *
     * @see GVRPose GVRSkeleton.getSkinPose GVRSkeleton.setPose GVRPose.setWorldRotations GVRPose.setWorldPositions
     */
    public GVRPose   getPose()  { return mPose; }

    /**
     * Make a pose for this skeleton in the given coordinate space.
     */
    public GVRPose	 makePose(GVRPose.PoseSpace coordspace)
    {
        GVRPose	pose = new GVRPose(this, coordspace);
        int		nbones = getNumBones();

        switch (coordspace)
        {
            case BIND_POSE_RELATIVE:
            pose.clearRotations();
            break;

            case BIND_POSE:
            pose.copy(mBindPose);
            break;

            default:
            pose.copy(mPose);
            break;
        }
        return pose;
    }

    /**
     * The skin pose is the pose the skeleton is currently in relative to it's bind pose.
     * This differs from the current pose {@link GVRSkeleton.getPose} which returns a pose
     * with locations and orientations relative to the skeleton's root joint.
     * The skin pose is a reference to an internal GVRPose object that changes dynamically.
     * Unlike the bind pose, which is referenced when a GVRSkeleton object is copied or cloned,
     * the skin pose is duplicated and cannot be shared across skeletons.
     *
     * @see GVRkeleton.setPose GVRSkeleton.setPose GVRPose
     */
    public final GVRPose getSkinPose()
    {
        return mSkinPose;
    }

   /**
    * Sets the the current pose for this skeleton, making this skeleton the
    * @param pose	GVRPose containing rotations and positions
    *
    * Sets the the current pose for this skeleton, making this skeleton the
    * owner of the pose. The pose can be shared by multiple engines which both read and write it.
    * The current pose contains the current orientation and position of the bones in the skeleton,
    * relative to bones at the origin along the bone axis (It is NOT relative to the bind pose).
    * When the current pose is updated, it causes the skeleton to modify the matrices
    * attached to the models in the target hierarchy and the bone matrices for each child Skin.
    *
    * @see GVRSkeleton.getPose GVRSkeleton.restoreBindPose GVRSkeleton.setBindPose
    */
    public void setPose(GVRPose pose)
    {
        GVRSkeleton skel = pose.getSkeleton();
        if (pose == mPose)
            return;
        if (skel != this)
            throw new IllegalArgumentException("setPose: input pose has incompatible skeleton");
        pose.sync();
        mPose = pose;
    }

    /**
     * Change the current pose of the skeleton.
     * @param newpose	new pose to apply.
     *
     * Establishes the current pose of a skeleton - supports all pose coordinate spaces.
     * If a bone is not present in the hierarchy or skin being animated, it's input is ignored.
     * If a bone is locked, it's input is also ignored.
     *
     * The input rotations may be absolute world rotations relative to the bone axis or they can be
     * relative to the bind pose of the skeleton. If a transformation has been applied to the target model,
     * this function will reverse it before applying the pose. This function does not affect the bind pose.
     *
     * @see GVRSkeleton.setPose GVRSkeleton.setBindPose GVRPose
     */
    public void applyPose(GVRPose newpose)
    {
        int				numbones = getNumBones();
        Quaternionf		root = new Quaternionf(0,0,0,1);
        GVRPose			curpose = getPose();
        GVRPose.PoseSpace space = newpose.getPoseSpace();

        if (space == GVRPose.PoseSpace.BIND_POSE_RELATIVE)
        {
            for (int i = 0; i < numbones; ++i)
            {
                GVRPose.Bone	bone = newpose.getBone(i);
                Quaternionf		wr = bone.WorldRot;

                if (bone.Changed == 0)
                    continue;
                if ((mBoneOptions[i] & BONE_LOCK_ROTATION) == 0)
                    continue;
                wr.mul(mBindPose.getWorldRotation(i));
                curpose.setWorldRotation(i, wr);
            }
        }
        else if (space == GVRPose.PoseSpace.SKELETON)
        {
		/*
		 * Get the pose in the coordinate space of the skeleton
		 * by applying the inverse of the transform on the
		 * target hierarchy root.
		 */
            GVRSceneObject owner = getOwnerObject();
            if (owner != null)
            {
                Matrix4f mtx = owner.getTransform().getModelMatrix4f();
                root.setFromUnnormalized(mtx);
                root.conjugate();
                root.normalize();
            }
            newpose.sync();
            Quaternionf q = new Quaternionf();
            for (int i = 0; i < numbones; ++i)
            {
                q.set(root);
                if ((mBoneOptions[i] & BONE_LOCK_ROTATION) == 0)
                {
                    q.mul(newpose.getWorldRotation(i));
                    curpose.setWorldRotation(i, q);
                }
            }
        }
        else if (space == GVRPose.PoseSpace.SKELETON)
        {
            if (curpose != newpose)
            {
                for (int i = 0; i < numbones; ++i)
                {
                    if ((mBoneOptions[i] & BONE_LOCK_ROTATION) == 0)
                        curpose.setWorldRotation(i, newpose.getWorldRotation(i));
                }
            }
        }
    }

    /**
     * Restore the skeleton to the bind pose.
     * The bind pose is the default pose of the skeleton when not being animated.
     * It is initialized as the pose the skeleton is when it is first attached
     * to the Skeleton but you can change it with SetBindPose.
     *
     * @see GVRSkeleton.setBindPose GVRSkeleton.getBindPose GVRPose
     */
    public void restoreBindPose()
    {
        mPose.copy(mBindPose);
    }

    /**
     * Gets the bone index for the parent of the specified bone.
     * @param boneindex	index of bone whose parent to set.
     *
     * @return parent index or -1 for root bone
     *
     * @see GVRSkeleton.getBoneIndex
     */
    public int getParentBoneIndex(int boneindex)
    {
        return mParentBones[boneindex];
    }

    /**
     * Get the bone index for the given scene object.
     * @param bone GVRSceneObject bone to search for
     *
     * @return bone index or -1 for root bone
     *
     * @see GVRSkeleton.setBone
     */
    public int getBoneIndex(GVRSceneObject bone)
    {
        for (int i = 0; i < getNumBones(); ++i)
            if (mBones.get(i) == bone)
                return i;
        return -1;
    }

    /**
     * Get the bone index for the bone with the given name.
     * @param bonename	string identifying the bone whose index you want
     *
     * @return 0 based bone index or -1 if bone with that name is not found.
     */
    public int getBoneIndex(String bonename)
    {
        for (int i = 0; i < getNumBones(); ++i)
            if (mBoneNames.equals(bonename))
                return i;
        return -1;
    }

    /**
     * Gets the number of bones in the skeleton.
     * The number of bones is established when the skeleton is first created
     * and cannot be subsquently changed;
     */
    public int getNumBones() { return mParentBones.length; }

    /**
     * Get the bone Z axis which defines the bone coordinate system.
     * The default bone coordinate system has the Z axis of
     * the bone at (0, 0, 1).
     */
    public Vector3f getBoneAxis()   { return mBoneAxis; }

    /**
     * Set the bone Z axis which defines the bone coordinate system.
     * The default bone coordinate system has the Z axis of
     * the bone at (0, 0, 1).
     */
    public void setBoneAxis(Vector3f boneAxis)  { mBoneAxis = boneAxis; }

   /**
    * Sets the name of the designated bone.
    * @param boneindex	zero based index of bone to rotate
    * @param bonename	string with name of bone
    *
    * Sets the name of the designated bone.
    *
    * @see GVRSkeleton.getBoneName
    */
    public void     setBoneName(int boneindex, String bonename)
    {
        mBoneNames[boneindex] = bonename;
    }

    /**
     * Get the name of a bone given it's index.
     * @param boneindex index of bone to get
     * @return name of bone or null if not found
     */
    public String getBoneName(int boneindex) { return mBoneNames[boneindex]; }

   /**
    * Get the scene object representing the given bone.
    * @param boneindex index of bone to get
    * @return GVRSceneObject whose transform represents the bone.
    */
    public GVRSceneObject getBone(int boneindex) { return mBones.get(boneindex); }

    /**
     * Set rotation and physics options for this bone
     * @param boneindex	0 based bone index
     * @param options	options to control how this bone moves
     *					BONE_PHYSICS will use physics (rigid body dynamics) to calculate
     *					the motion of the bone
     *					BONE_LOCK_ROTATION will lock the bone rotation, freezing its current
     *					local rotation
     *
     * @see GVRSkeleton.setLocalRotation GVRSkeleton.setWorldRotation
     */
    public void	 setBoneOptions(int boneindex, int options)
    {
        mBoneOptions[boneindex] = options;
    }

    /**
     * Get rotation and physics options for this bone
     * @param boneindex	0 based bone index
     *
     * @see GVRSkeleton.setBoneOptions
     */
    public int getBoneOptions(int boneindex)
    {
        return mBoneOptions[boneindex];
    }

    /**
     * Determine if given bone is locked or not.
     * @param boneindex	0 based bone index
     */
    public boolean isLocked(int boneindex)
    {
        return (mBoneOptions[boneindex] & BONE_LOCK_ROTATION) != 0;
    }

    /**
     * Derive indices of a set of bones from a hierarchy based on their names.
     * @param bonenames	array of strings with names of bones to map
     * @param bonemap	integer array to get the indices of mapped bones
     * @param numbones	number of bones to map (size of bonenames and bonemap)
     *
     * Populates an array which maps a set of bone names to the bones in this skeleton.
     * Upon return, the bonemap array will contain an entry for each string in bonenames.
     * If a bone in the skeleton is found which contains the string, it's index is
     * stored. Otherwise the entry is -1.
     */
    public int[] makeBoneMap(String[] bonenames)
    {
        int[] bonemap = new int[bonenames.length];
        for (int i = 0; i < bonenames.length; ++i)
        {
            int	boneindex = -1;
            String searchfor = bonenames[i];

            bonemap[i] = -1;
            if (searchfor == null)
                continue;
            for (int b = 0; b < getNumBones(); ++b)
            {
                String bonename = getBoneName(b);
                if ((bonename != null) && (bonename.indexOf(searchfor) >= 0))
                {
                    boneindex =  b;
                    break;
                }
            }
            bonemap[i] = boneindex;
        }
        return bonemap;
    }

    /**
     * Updates the transformations on the bone shapes in the target hierarchy
     * associated with the skeleton.
     */
    protected void    updateBindPose()
    {
        int numbones = getNumBones();
        Matrix4f localmtx = new Matrix4f();
        Matrix4f worldmtx = new Matrix4f();

        mBindPose.sync();
        for (int i = 0; i < numbones; ++i)
        {
            GVRPose.Bone posebone = mBindPose.getBone(i);
            GVRSceneObject bone = mBones.get(i);

            if (posebone.ParentID < 0)
                posebone.ParentID = mParentBones[i];
            mBindPose.getWorldMatrix(i, worldmtx);
            if (mParentBones[i] >= 0)
            {
                mBindPose.getWorldMatrix(posebone.ParentID, localmtx);
                localmtx.invert();            // invert total matrix from parent to root
                localmtx.mul(worldmtx);        // isolate local matrix
            } else
                localmtx = worldmtx;
            worldmtx.invert();
            mInverseBindPose[i] = worldmtx;
            if (bone != null)
            {
                bone.getTransform().setModelMatrix(localmtx);
            }
        }
    }

    /**
     * Examines the child engines for the skeleton looking for Transformers which match the
     * bones in the skeleton. Use this function instead of AttachBones if you have already created
     * the bone Transformer hierarchy and want to attach it to the skeleton.
     */
    public void findBones(final GVRPose savepose)
    {
        GVRSceneObject owner = getOwnerObject();

        if (owner == null)
        {
            return;
        }
        if (savepose != null)
        {
            savepose.copy(mPose);
        }
        GVRSceneObject.SceneVisitor visitor = new GVRSceneObject.SceneVisitor()
        {
            @Override
            public boolean visit(GVRSceneObject newbone)
            {
                String bonename = newbone.getName();

                if (bonename.isEmpty())			// ignore nodes without names
                    return true;
                int boneindex = getBoneIndex(bonename);
                if (boneindex >= 0)					// a bone we recognize?
                {
                    GVRSceneObject curbone = mBones.get(boneindex);
                    mBones.set(boneindex, newbone);
                    if (savepose != null)
                    {
                        savepose.setWorldMatrix(boneindex, newbone.getTransform().getModelMatrix4f());
                    }
                }
                return true;
            }
        };
        owner.forAllDescendants(visitor);
    }

    /**
     * Applies the matrices computed from the current pose to update the
     * target hierarchy and the child skins.
     */
    private void update()
    {
        GVRPose			pose = mPose;
        boolean			changed;
        Matrix4f		mpose = new Matrix4f();

    /*
     * Update the pose based on bone animations.
     * If any of the bones are animated, they will be marked as changed.
     */
        Quaternionf q = new Quaternionf();
        Vector3f p = new Vector3f();
        for (int i = 0; i < getNumBones(); ++i)
        {
            GVRSceneObject bone =  mBones.get(i);
            if (bone == null)
                continue;
            GVRTransform trans = bone.getTransform();
            if ((mBoneOptions[i] & BONE_ANIMATE) != 0)
            {
                q.set(trans.getRotationX(), trans.getRotationY(), trans.getRotationZ(), trans.getRotationW());
                q.normalize();
                pose.setLocalRotation(i, q);
                if (i == 0)
                {
                    p.set(trans.getPositionX(), trans.getPositionY(), trans.getPositionZ());
                    p.add(mRootOffset);
                    pose.setPosition(p);
                }
            }
        }
        /*
         * Now we update the skin matrix from the current pose
         */
        pose.sync();
        for (int i = 0; i < getNumBones(); ++i)
        {
            GVRSceneObject bone =  mBones.get(i);
            if (bone != null)
            {
                GVRTransform trans = bone.getTransform();
                q = pose.getLocalRotation(i);
                trans.setRotation(q.w, q.x, q.y, q.z);
                if (i == 0)
                {
                    p = pose.getLocalPosition(i);
                    trans.setPosition(p.x, p.y, p.z);
                }
            }
            pose.getWorldMatrix(i, mpose);
            mpose.mul(mInverseBindPose[i]);
            mSkinPose.setWorldMatrix(i, mpose);
        }
    }
};