package org.gearvrf.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/*!
 * Set of transformations on the bones of a skeleton.
 *
 * A pose is associated with a specific skeleton.
 * It contains a matrix for every bone in that skeleton.
 * You can view a pose in local space where each bone matrix is relative to its parent bone.
 * You can also view it in world space where each bone matrix gives the world space joint orientation
 * and the world space joint position.
 *
 * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
 * The pose orients and positions each bone in the skeleton with respect to this initial state.
 * Usually the bones are in a hierarchy and transformations on a parent bone apply to the
 * child bones as well.
 *
 * Each skeleton has a current pose. Often the current pose of a skeleton is used to
 * drive a skinned animation.
 *
 * @see GVRTransform GVRSkeleton
  */
public class GVRPose
{
    static final float EPSILON = Float.intBitsToFloat(Float.floatToIntBits(1f) + 1);
    protected GVRSkeleton mSkeleton;
    protected PoseSpace	  mPoseSpace;
    private boolean	      mNeedSync;
    private boolean       mChanged;
    private Bone[]        mBones;

    public enum PoseSpace
    {
        BIND_POSE_RELATIVE,	// pose angles are relative to the skeleton bind pose
        SKELETON,			// pose angles are relative to the root joint of the skeleton
        BIND_POSE,			// pose angles represent the bind pose
    };

    /**
     * Constructs a pose based on the specified skeleton.
     * @param skel	skeleton associated with the pose.
     * @param space	coordinate space for the pose (SKELETON or BIND_POSE_RELATIVE)
     *
     * Constructs a pose for the given skeleton.
     * Initially all of the bone matrices are identity.
     *
     * @see GVRPose.setSkeleton
     */
    public GVRPose(GVRSkeleton skel)
    {
        this(skel, PoseSpace.SKELETON);
    }

    public GVRPose(GVRSkeleton skel, PoseSpace space)
    {
        mSkeleton = skel;
        mPoseSpace = space;
        mBones = new Bone[skel.getNumBones()];
        for (int i = 0; i < mBones.length; ++i)
        {
            int	pid = skel.getParentBoneIndex(i);
            mBones[i].ParentID = pid;
        }
        mSkeleton = skel;
    }

   /**
    * @return number of bones in the skeleton associated with this pose.
    * If there is no skeleton associated with the pose, 0 is returned.
    */
    public int          getNumBones() { return mSkeleton.getNumBones(); }

    /**
     * Gets the coordinate space of the pose.
     * @return pose coordnate space.
     */
    public PoseSpace	getPoseSpace() { return mPoseSpace; }

    /**
     * Get the skeleton for this pose.
     * The skeleton is established when the pose is created
     * and cannot be modified.
     * @return skeleton the pose applies to.
     */
    public GVRSkeleton	getSkeleton() { return mSkeleton; }

    /**
     * Clear all the rotations in the pose.
     *
     * @see GVRPose.setLocalRotation GVRPose.getNumBones GVRSkeleton.setBoneAxis GVRPose.setWorldRotations GVRPose.setWorldMatrix
     */
    public void clearRotations()
    {
        for (Bone bone : mBones)
        {
            bone.LocalRot.set(0, 0, 0, 1);
            bone.WorldRot.set(0, 0, 0, 1);
            bone.Changed = 0;
        }
        mNeedSync = false;
        mChanged = true;
    }

    /**
     * Get the world position of the given bone (relative to hierarchy root).
     * @param boneindex index of bone whose position is wanted.
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton. This function gets the world space position of the designated bone.
     *
     * @return world position for the given bone.
     *
     * @see GVRPose.setWorldPositions
     */
    public Vector3f     getWorldPosition(int boneindex) { return mBones[boneindex].WorldPos; }

    /**
     * Get the world positions of all the bones in this pose (relative to skeleton root).
     * @param dest	destination array to get world space joint positions
     *
     * The world space joint positions for each bone are copied into the
     * destination array as vectors in the order of their bone index.
     * The array must be as large as the number of bones in the skeleton
     * (which can be obtained by calling getNumBones).
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton. This function returns the world space bone positions
     * as an array of vectors.
     *
     * @see GVRPose.setWorldRotations GVRPose.setWorldMatrix GVRPose.setWorldPositions
     */
    public void	getWorldPositions(Vector3f[] positions)
    {
        for (int i = 0; i < mBones.length; ++i)
        {
            positions[i] = mBones[i].WorldPos;
        }
    }

    /**
     * Set the world positions for the bones in this pose (relative to skeleton root).
     * @param vecs	array of vectors with the positions in world coordinates
     *
     * The world space joint positions for each bone are copied from the
     * source array of vectors in the order of their bone index.
     * The array must be as large as the number of bones in the skeleton
     * (which can be obtained by calling GetNumBones).
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton. This function sets the world space bone positions
     * from an array of vectors. The bone orientations are unaffected and it is up to the
     * caller to make sure these positions are compatible with the current pose rotations.
     *
     * @see GVRPose.setWorldRotations GVRPose.setWorldMatrix GVRPose.getWorldPositions
     */
    public void	setWorldPositions(Vector3f[] positions)
    {
        Vector3f p = new Vector3f();
        boolean changed = false;
        for (int i = 0; i < mBones.length; ++i)
        {
            Bone bone = mBones[i];
            p.set(positions[i]);
            if (!bone.WorldPos.equals(p))
            {
                bone.WorldPos = p;
                calcLocal(bone);
                changed = true;
            }
        }
        mChanged = changed;
    }

    /**
     * Sets the world rotations for all the bones in this pose (relative to skeleton root).
     * @param quat		array of quaternions with the rotations in world coordinates
     *
     * The world space joint rotations for each bone are copied from the
     * source array of quaterions in the order of their bone index.
     * The order of the bones in the array must follow the order in the skeleton for this pose.
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton. This function sets the world space bone rotations
     * from an array of quaternions.The bone positions are unaffected.
     *
     * @see GVRPose.setLocalRotations GVRPose.setWorldMatrix GVRPose.getWorldRotations GVRPose.setWorldPositions GVRSkeleton.setBoneAxis GVRPose.getNumBones
     */
    public void setWorldRotations(Quaternionf[] rotations)
    {
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < mBones.length; ++i)
        {
            Bone bone = mBones[i];
            q.set(rotations[i]);
            q.normalize();
            if (bone.WorldRot.equals(q))
                continue;
            bone.WorldRot = q;
            if (mPoseSpace == PoseSpace.BIND_POSE_RELATIVE)
            {
                bone.LocalRot = q;
                bone.Changed |= Bone.LOCAL_ROT;
            }
            else
            {
                mNeedSync = true;
                bone.Changed |= Bone.WORLD_ROT;
                if (bone.ParentID < 0)
                    bone.LocalRot = q;
            }
        }
    }

    /**
     * Get the world matrix for this bone (relative to skeleton root).
     * @param boneindex	zero based index of bone to get matrix for
     * @param mtx		where to store bone matrix
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton.
     *
     * @return world matrix for the designated bone.
     *
     * @see  GVRPose.getWorldRotation GVRPose.getLocalRotation GVRPose.setWorldMatrix  GVRSkeleton.setBoneAxis
     */
    public void getWorldMatrix(int boneindex, Matrix4f mtx)
    {
        mBones[boneindex].getWorldMatrix(mtx);
    }

    /**
     * Set the world matrix for this bone (relative to skeleton root).
     * @param boneindex	zero based index of bone to set matrix for
     * @param mtx		new bone matrix
     *
     * Sets the world matrix for the designated bone.
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton.
     *
     * @see GVRPose.getWorldRotation GVRPose.setLocalRotation GVRPose.getWorldMatrix GVRPose.getWorldPositions GVRSkeleton.setBoneAxis
     */
    public void setWorldMatrix(int boneindex, Matrix4f mtx)
    {
        Bone	  bone = mBones[boneindex];
        int		  parentid = bone.ParentID;
        Vector3f  p = new Vector3f();
        Quaternionf	rot = new Quaternionf();

        rot.setFromUnnormalized(mtx);
        mChanged = true;
        rot.normalize();
        mtx.getTranslation(p);
        bone.WorldRot = rot;
        bone.WorldPos = p;
        if (parentid < 0)
        {
            bone.LocalRot = bone.WorldRot;
            bone.LocalPos = bone.WorldPos;
        }
        bone.Changed |= Bone.WORLD_POS | Bone.WORLD_ROT;
        mNeedSync = true;
    }

    /**
     * Get the world rotations for all the bones in this pose.
     * @param dest	destination array to get world space joint rotations
     *
     * The world space joint rotations for each bone are copied into the
     * destination array as quaterions in the order of their bone index.
     * The array must be as large as the number of bones in the skeleton
     * (which can be obtained by calling GetNumBones).
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation and position of the bone relative
     * to the root of the skeleton. This function returns the world space bone rotations
     * as an array of quaternions.
     *
     * @see GVRPose.setWorldRotatons GVRPose.getWorldRotation GVRPose.getWorldMatrix GVRPose.getNumBones GVRSkeleton.setBoneAxis
     */
    public void getWorldRotations(Quaternionf[] rotations)
    {
        sync();
        for (int i = 0; i < mBones.length; ++i)
        {
            rotations[i] = mBones[i].WorldRot;
        }
    }

    /**
     * Gets the world location of a bone (relative to hierarchy root).
     * @param boneindex	zero based index of bone whose rotation is wanted.
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone matrix expresses the orientation of the bone relative
     * to the root bone of the skeleton.
     *
     * @return world rotation for the designated bone as a quaternion.
     *
     * @see GVRPose.setWorldRotation GVRPose.setWorldRotations GVRPose.setWorldMatrix GVRSkeleton.setBoneAxis
     */
    public Quaternionf	getWorldRotation(int boneindex) { return mBones[boneindex].WorldRot; }

    /**
     * Sets the world rotation for the designated bone.
     * @param boneindex	zero based index of bone to set matrix for
     * @param rot		new bone world rotation
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The world bone rotation expresses the orientation of the bone relative
     * to the root of the skeleton.
     *
     * This function recomputes the local bone rotation and the world bone position.
     *
     * @see GVRPose.getWorldRotation GVRPose.setLocalRotation GVRPose.getWorldMatrix GVRPose.getWorldPositions
     */
    public void setWorldRotation(int boneindex, Quaternionf rot)
    {
        Bone	bone = mBones[boneindex];
        int		parentid = bone.ParentID;
        Quaternionf	q = new Quaternionf(rot);

        q.normalize();
        if (bone.WorldRot.equals(q))
            return;
        if (mSkeleton.isLocked(boneindex))
            return;
        bone.WorldRot = q;
        if (mPoseSpace == PoseSpace.BIND_POSE_RELATIVE)
        {
            bone.Changed |= Bone.LOCAL_ROT;
            bone.LocalRot = bone.WorldRot;
            return;
        }
	/*
	 * Indicate world matrix has changed for this bone
	 */
        bone.Changed |= Bone.WORLD_ROT;
        mNeedSync = true;
        mChanged = true;
        if (parentid < 0)
            bone.LocalRot = q; // save new world rotation
    }

    /**
     * Get the local rotation matrix for this bone (relative to parent).
     * @param boneindex	zero based index of bone to get matrix for
     * @param mtx		where to store bone matrix
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The local bone matrix expresses the orientation and position of the bone relative
     * to the parent bone.
     *
     * @return local matrix for the designated bone.
     *
     * @see  GVRPose.getWorldRotation GVRPose.getLocalRotation GVRPose.setLocalMatrix  GVRSkeleton.setBoneAxis
     */
    public void getLocalMatrix(int boneindex, Matrix4f mtx) { mBones[boneindex].getLocalMatrix(mtx); }

    /**
     * Sets the local rotations for all the bones in this pose.
     * @param quat		array of quaternions with the rotations for each bone.
     *					the angles are in the bone's local coordinate system.
     *
     * The local space joint rotations for each bone are copied from the
     * source array of quaterions in the order of their bone index.
     * The order of the bones in the array must follow the order in the skeleton for this pose.
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The local bone matrix expresses the orientation and position of the bone relative
     * to it's parent. This function sets the local orientations of all the bones from an
     * array of quaternions. The position of the bones are unaffected.
     *
     * @see GVRPose.setLocalRotation GVRPose.getNumBones GVRSkeleton.setBoneAxis GVRPose.setWorldRotations GVRPose.setWorldMatrix
     */
    public void setLocalRotations(Quaternionf[] rotations)
    {
        int i = 0;
        for (Bone bone : mBones)
        {
            if (mSkeleton.isLocked(i))
                continue;
            Quaternionf	q = rotations[i];

            q.normalize();
            ++i;
            if (bone.LocalRot == q)
                return;
            bone.LocalRot = q;
            if (mPoseSpace == PoseSpace.BIND_POSE_RELATIVE)
                bone.WorldRot = q;
            bone.Changed |= Bone.LOCAL_ROT;
        }
        mChanged = true;
        mNeedSync = true;
    }

    /**
     * Gets the local rotation for a bone given its index.
     *
     * @fn const Quat& Pose::GetLocalRotation(int boneindex)
     * @param boneindex	zero based index of bone whose rotation is wanted.
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The local bone matrix expresses the orientation and position of the bone relative
     * to it's parent. This function returns the rotation component of that matrix as a quaternion.
     *
     * @return local rotation for the designated bone as a quaternion.
     *
     * @see GVRPose.setLocalRotation GVRPose:.setWorldRotations GVRPose.setWorldMatrix GVRSkeleton.setBoneAxis
     */
    Quaternionf getLocalRotation(int boneindex)
    {
        return mBones[boneindex].LocalRot;
    }

    /**
     * Sets the local rotation for the designated bone.
     * @param boneindex	zero based index of bone to rotate
     * @param quat		quaternion with the rotation for the named bone
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The local bone matrix expresses the orientation and position of the bone relative
     * to it's parent. This function sets the rotation component of that matrix from a quaternion.
     * The position of the bone is unaffected.
     *
     * @see GVRPose.setLocalRotations GVRPose.setWorldRotations GVRPose.setWorldMatrix GVRSkeleton.setBoneAxis
     */
    public void setLocalRotation(int boneindex, Quaternionf newrot)
    {
        Bone bone = mBones[boneindex];
        Quaternionf q = new Quaternionf(newrot);

        if (mSkeleton.isLocked(boneindex))
            return;
        q.normalize();
        if (bone.LocalRot == q)
            return;
        mChanged = true;
        bone.LocalRot = q;
        bone.Changed |= Bone.LOCAL_ROT;
        if (mPoseSpace == PoseSpace.BIND_POSE_RELATIVE)
        {
            bone.WorldRot = q;
            return;
        }
        mNeedSync = true;
        if (bone.ParentID < 0)
            bone.WorldRot = q;
    }

    /**
     * Gets the local position (relative to the parent) of a bone.
     * @param boneindex	zero based index of bone whose position is wanted.
     *
     * All bones in the skeleton start out at the origin oriented along the bone axis (usually 0,0,1).
     * The pose orients and positions each bone in the skeleton with respect to this initial state.
     * The local bone matrix expresses the orientation and position of the bone relative
     * to it's parent. This function returns the translation component of that matrix.
     *
     * @return local translation for the designated bone.
     *
     * @see GVRPose.setLocalRotation GVRPose.setWorldRotations GVRPose.setWorldMatrix GVRSkeleton.setBoneAxis
     */
    public Vector3f     getLocalPosition(int boneindex)
    {
        return mBones[boneindex].LocalPos;
    }

    /**
     * Compares two poses to see if they are the same
     * @param src pose to compare with
     * @return true if both objects represent the same pose
     */
    public boolean  equals(GVRPose src)
    {
        int		    numbones = getNumBones();
        boolean	    same = true;
        float       tolerance = 3 * EPSILON;

        if (numbones != src.getNumBones())
            return false;
        sync();
        for (int i = 0; i < numbones; ++i)
        {
            if (!mBones[i].equals(src.getBone(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies the contents of the input pose into this pose
     * @param src pose to copy
     */
    public void  copy(GVRPose src)
    {
        int numbones = getNumBones();

        if (getSkeleton() != src.getSkeleton())
            throw new IllegalArgumentException("GVRPose.copy: input pose is incompatible with this pose");
        mPoseSpace = src.getPoseSpace();
        mChanged = true;
        mNeedSync = src.mNeedSync;
        for (int i = 0; i < numbones; ++i)
        {
            mBones[i] = src.getBone(i);
        }
    }

    /**
     * Sets the world position of the root bone and propagates to all children.
     * @param pos	new world position of root bone
     *
     * This has the effect of moving the overall skeleton to a new position
     * without affecting the orientation of it's bones.
     *
     * @see GVRPose.setWorldPositions GVRPose.getWorldPosition
     */
    public void		setPosition(Vector3f pos)
    {
        Vector3f	diff = new Vector3f(pos);
        diff.sub(mBones[0].WorldPos, diff);	// how much has root bone moved?
        if (diff.lengthSquared() <= EPSILON)
            return;
        sync();
        mBones[0].LocalPos = pos;
        for (int i = 0; i < mBones.length; ++i)
        {
            mBones[i].WorldPos.add(diff);        // move all the bones by the same amount
        }
        mChanged = true;
    }

    /**
     * Synchronize world rotations and local rotations.
     * Positions are unaffected.
     */
    public boolean	sync()
    {
        if (!mNeedSync)
            return false;
        mNeedSync = false;
        if (mPoseSpace == PoseSpace.BIND_POSE_RELATIVE)
            return false;
        Quaternionf	q = new Quaternionf();
        Vector3f p = new Vector3f();
        for (int i = 0; i < mBones.length; ++i)
        {
            Bone 	bone = mBones[i];
            int		pid = bone.ParentID;
            boolean	update;

            if (pid < 0)							// root bone?
                continue;
            update = (mBones[pid].Changed & (Bone.WORLD_ROT | Bone.LOCAL_ROT)) != 0;
            if (!mSkeleton.isLocked(i))				// bone not locked?
            {
                if ((bone.Changed & Bone.WORLD_POS) != 0)	// world matrix changed?
                {
                    q = bone.WorldRot;
                    p = bone.WorldPos;
                    calcLocal(bone);					// calculate local rotation and position
                    continue;
                }
                if ((bone.Changed & Bone.WORLD_ROT) != 0)
                {										// world rotation changed?
                    q = bone.WorldRot;
                    calcHybrid(bone);					// calculate local rotation, world position
                    continue;
                }
            }
            if (update ||								// use local pos & rot?
                (bone.Changed & (Bone.LOCAL_ROT | Bone.WORLD_ROT)) != 0)
            {
                q = bone.LocalRot;
                bone.Changed = Bone.LOCAL_ROT;
                calcWorld(bone);						// update world rotation & position
            }
        }
        for (int i = 0; i < mBones.length; ++i)
        {
            mBones[i].Changed = 0;
        }
        return true;
    }

    /**
     * Calculates the world position and rotation based on the
     * local position and rotation (WorldRot, WorldPos from LocalRot, LocalPos)
     */
    protected void		calcWorld(Bone bone)
    {
        Matrix4f localMatrix = new Matrix4f();
        Matrix4f worldMatrix = new Matrix4f();
        Vector3f worldPos = new Vector3f();
        Quaternionf	q = new Quaternionf();

        getWorldMatrix(bone.ParentID, worldMatrix);	// WorldMatrix(parent)
        bone.getLocalMatrix(localMatrix);
        worldMatrix.mul(localMatrix);               // WorldMatrix = WorldMatrix(parent) * LocalMatrix
        worldMatrix.getTranslation(worldPos);
        q.setFromUnnormalized(worldMatrix);
        q.normalize();
        bone.WorldRot = q;					        // save new world rotation
        bone.WorldPos = worldPos;					// save new world position
     }

    /**
     * Calculates the local translation and rotation for a bone.
     * Assumes WorldRot and WorldPos have been calculated for the bone.
     */
    protected void		calcLocal(Bone bone)
    {
        if (bone.ParentID < 0)
        {
            bone.LocalPos = bone.WorldPos;
            bone.LocalRot = bone.WorldRot;
            return;
        }
	/*
	 * WorldMatrix = WorldMatrix(parent) * LocalMatrix
	 * LocalMatrix = INVERSE[ WorldMatrix(parent) ] * WorldMatrix
	 */
        Matrix4f localMatrix = new Matrix4f();
        Matrix4f worldMatrix = new Matrix4f();
        Vector3f localPos = new Vector3f();
        Quaternionf	q = new Quaternionf();

        getWorldMatrix(bone.ParentID, localMatrix);	// WorldMatrix(par)
        localMatrix.invert();						// INVERSE[ WorldMatrix(parent) ]
        bone.getWorldMatrix(worldMatrix);
        localMatrix.mul(worldMatrix);				// INVERSE[ WorldMatrix(parent) ] * WorldMatrix
        localMatrix.getTranslation(localPos);
        q.setFromUnnormalized(localMatrix);
        q.normalize();
        bone.LocalRot = q;						    // save local rotation
        bone.LocalPos = localPos;				    // save local position
    }

    /**
     * Calculates the local rotation and world translation for a bone.
     * Assumes WorldRot and WorldPos have been calculated for the bone.
     */
    protected void		calcHybrid(Bone bone)
    {
        if (bone.ParentID < 0)
        {
            bone.LocalPos = bone.WorldPos;
            bone.LocalRot = bone.WorldRot;
            return;
        }
    /*
        WorldPos * WorldRot = WorldMatrix(par) * LocalPos * LocalRot
        WorldRot = WorldRot(parent) * LocalRot
        LocalRot = INVERSE[ WorldRot(par) ] * WorldRot
        WorldPos * WorldRot(par) = WorldMatrix(par) * LocalPos
        WorldPos = WorldMatrix(par) * LocalPos * INVERSE[WorldRot(par)]
    */
        Matrix4f	tmp1 = new Matrix4f();
        Matrix4f    tmp2 = new Matrix4f();
        Quaternionf	q1 = new Quaternionf();
        Quaternionf q2 = new Quaternionf();
        Vector3f	p = new Vector3f();

        getWorldMatrix(bone.ParentID, tmp2);		// WorldMatrix(par)
        tmp2.invert();								// INVERSE[ WorldMatrix(parent) ]
        bone.getWorldMatrix(tmp1);
        tmp2.mul(tmp1, tmp1);						// INVERSE[ WorldMatrix(parent) ] * WorldMatrix
        q2.setFromNormalized(tmp1);
        q2.normalize();
        bone.LocalRot = q2;							// save local rotation
        getWorldMatrix(bone.ParentID, tmp1);		// WorldMatrix(par)
        tmp2.setTranslation(bone.LocalPos);			// LocalPos * INVERSE( WorldRot(par) ]
        tmp1.mul(tmp2);								// WorldMatrix(par) * LocalPos * INVERSE( WorldRot(par) ]
        tmp1.getTranslation(p);						// WorldPos
        bone.WorldPos = p;
    }

    /**
     * Sets the local position of the root bone
     * @param pos new position
     */
    public void		setLocalPosition(Vector3f pos)
    {
        Bone bone = mBones[0];
        bone.WorldPos = pos;
        bone.LocalPos = pos;
    }

    public void     setPoseSpace(PoseSpace s) { mPoseSpace = s; }
    public Bone		getBone(int boneindex) { return mBones[boneindex]; }


/**
 * Internal structure used to maintain information about each bone.
 */
static class Bone
{
    public int ParentID;            //! index of parent bone
    public int Changed;             //! WORLD_ROT, LOCAL_ROT, WORLD_POS
    public Quaternionf LocalRot;    //! local rotation
    public Quaternionf WorldRot;    //! world rotation
    public Vector3f WorldPos;       //! world position
    public Vector3f LocalPos;       //! local position

    public static final int LOCAL_ROT = 1;
    public static final int WORLD_ROT = 2;
    public static final int WORLD_POS = 4;

    public Bone()
    {
        ParentID = -1;
        LocalRot = new Quaternionf(0.0f, 0.0f, 0.0f, 1.0f);
        WorldRot = new Quaternionf(0.0f, 0.0f, 0.0f, 1.0f);
        LocalPos = new Vector3f(0.0f, 0.0f, 0.0f);
        WorldPos = new Vector3f(0.0f, 0.0f, 0.0f);
        Changed = 0;
    }

    public Bone(Bone src)
    {
        ParentID = src.ParentID;
        LocalRot = src.LocalRot;
        LocalPos = src.LocalPos;
        WorldRot = src.WorldRot;
        WorldPos = src.WorldPos;
        Changed = src.Changed;
    }

    public void getWorldMatrix(Matrix4f mtx)
    {
        mtx.rotation(WorldRot);
        mtx.setTranslation(WorldPos);
    }

    public void  getLocalMatrix(Matrix4f mtx)
    {
        mtx.rotate(LocalRot);
        mtx.setTranslation(LocalPos);
    }

    public boolean equals(Bone src)
    {
        if (ParentID != src.ParentID)
            return false;
        float   d = WorldPos.distanceSquared(src.WorldPos);
        boolean same = true;

        if (d > EPSILON)
        {
            same = false;
        }
        d = LocalPos.distanceSquared(src.LocalPos);
        if (d > EPSILON)
        {
            same = false;
        }
        Vector4f qt = new Vector4f(
                WorldRot.x - src.WorldRot.x,
                WorldRot.y - src.WorldRot.y,
                WorldRot.z - src.WorldRot.z,
                WorldRot.w - src.WorldRot.w);
        d = qt.lengthSquared();
        if (d > EPSILON)
        {
            same = false;
        }
        qt.set(LocalRot.x - src.LocalRot.x,
               LocalRot.y - src.LocalRot.y,
               LocalRot.z - src.LocalRot.z,
               LocalRot.w - src.LocalRot.w);
        d = qt.lengthSquared();
        if (d > EPSILON)
        {
            same = false;
        }
        return same;
    }
}

};



