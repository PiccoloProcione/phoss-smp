/**
 * Copyright (C) 2015-2020 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * The Original Code is Copyright The Peppol project (http://www.peppol.eu)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.helger.phoss.smp.backend.sql.mgr;

import java.util.List;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;

import org.eclipse.persistence.config.CacheUsage;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.callback.CallbackList;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.CommonsHashMap;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.collection.impl.ICommonsMap;
import com.helger.commons.equals.EqualsHelper;
import com.helger.commons.mutable.MutableBoolean;
import com.helger.commons.state.EChange;
import com.helger.commons.string.StringHelper;
import com.helger.db.jpa.JPAExecutionResult;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.phoss.smp.backend.sql.AbstractSMPJPAEnabledManager;
import com.helger.phoss.smp.backend.sql.model.DBOwnership;
import com.helger.phoss.smp.backend.sql.model.DBOwnershipID;
import com.helger.phoss.smp.backend.sql.model.DBServiceGroup;
import com.helger.phoss.smp.backend.sql.model.DBServiceGroupID;
import com.helger.phoss.smp.backend.sql.model.DBUser;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroup;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroupCallback;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroupManager;
import com.helger.phoss.smp.domain.servicegroup.SMPServiceGroup;
import com.helger.phoss.smp.exception.SMPInternalErrorException;
import com.helger.phoss.smp.exception.SMPNotFoundException;
import com.helger.phoss.smp.exception.SMPSMLException;
import com.helger.phoss.smp.exception.SMPServerException;
import com.helger.phoss.smp.exception.SMPUnknownUserException;
import com.helger.phoss.smp.smlhook.IRegistrationHook;
import com.helger.phoss.smp.smlhook.RegistrationHookException;
import com.helger.phoss.smp.smlhook.RegistrationHookFactory;
import com.helger.photon.audit.AuditHelper;

public final class SMPServiceGroupManagerSQL extends AbstractSMPJPAEnabledManager implements ISMPServiceGroupManager
{
  private final CallbackList <ISMPServiceGroupCallback> m_aCBs = new CallbackList <> ();

  public SMPServiceGroupManagerSQL ()
  {}

  @Nonnull
  @ReturnsMutableObject ("by design")
  public CallbackList <ISMPServiceGroupCallback> serviceGroupCallbacks ()
  {
    return m_aCBs;
  }

  @Nonnull
  public SMPServiceGroup createSMPServiceGroup (@Nonnull @Nonempty final String sOwnerID,
                                                @Nonnull final IParticipantIdentifier aParticipantID,
                                                @Nullable final String sExtension) throws SMPServerException
  {
    ValueEnforcer.notEmpty (sOwnerID, "OwnerID");
    ValueEnforcer.notNull (aParticipantID, "ParticpantID");
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("createSMPServiceGroup (" +
                    sOwnerID +
                    ", " +
                    aParticipantID.getURIEncoded () +
                    ", " +
                    (StringHelper.hasText (sExtension) ? "with extension" : "without extension") +
                    ")");

    final IRegistrationHook aHook = RegistrationHookFactory.getInstance ();
    final MutableBoolean aCreatedServiceGroup = new MutableBoolean (false);

    final JPAExecutionResult <?> ret;
    ret = doInTransaction ( () -> {
      final EntityManager aEM = getEntityManager ();

      // Check if the passed service group ID is already in use
      final DBServiceGroupID aDBServiceGroupID = new DBServiceGroupID (aParticipantID);
      DBServiceGroup aDBServiceGroup = aEM.find (DBServiceGroup.class, aDBServiceGroupID);
      if (aDBServiceGroup != null)
        throw new IllegalStateException ("The service group with ID " + aParticipantID.getURIEncoded () + " already exists!");

      final DBUser aDBUser = aEM.find (DBUser.class, sOwnerID);
      if (aDBUser == null)
        throw new SMPUnknownUserException (sOwnerID);

      {
        // It's a new service group - Create in SML and remember that
        // Throws exception in case of an error
        aHook.createServiceGroup (aParticipantID);
        aCreatedServiceGroup.set (true);
      }

      // Did not exist. Create it.
      final DBOwnershipID aDBOwnershipID = new DBOwnershipID (sOwnerID, aParticipantID);
      final DBOwnership aOwnership = new DBOwnership (aDBOwnershipID, aDBUser, (DBServiceGroup) null);
      aDBServiceGroup = new DBServiceGroup (aDBServiceGroupID, sExtension, aOwnership, null);
      aEM.persist (aDBServiceGroup);
      aEM.persist (aOwnership);
    });

    if (ret.isFailure ())
    {
      // Error writing to the DB
      if (aCreatedServiceGroup.booleanValue ())
      {
        // Undo creation in SML again
        try
        {
          aHook.undoCreateServiceGroup (aParticipantID);
        }
        catch (final RegistrationHookException ex)
        {
          LOGGER.error ("Failed to undoCreateServiceGroup (" + aParticipantID.getURIEncoded () + ")", ex);
        }
      }
    }

    if (ret.hasException ())
    {
      // Propagate contained exception
      final Exception ex = ret.getException ();
      if (ex instanceof SMPServerException)
        throw (SMPServerException) ex;
      if (ex instanceof RegistrationHookException)
        throw new SMPSMLException ("Failed to create '" + aParticipantID.getURIEncoded () + "' in SML", (RegistrationHookException) ex);
      throw new SMPInternalErrorException ("Error creating ServiceGroup '" + aParticipantID.getURIEncoded () + "'", ret.getException ());
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("createSMPServiceGroup succeeded");

    final SMPServiceGroup aSMPServiceGroup = new SMPServiceGroup (sOwnerID, aParticipantID, sExtension);
    AuditHelper.onAuditCreateSuccess (SMPServiceGroup.OT, aSMPServiceGroup.getID (), sOwnerID, aParticipantID.getURIEncoded (), sExtension);

    m_aCBs.forEach (x -> x.onSMPServiceGroupCreated (aSMPServiceGroup));
    return aSMPServiceGroup;
  }

  @Nonnull
  public EChange updateSMPServiceGroup (@Nonnull final IParticipantIdentifier aParticipantID,
                                        @Nonnull @Nonempty final String sNewOwnerID,
                                        @Nullable final String sExtension) throws SMPServerException
  {
    ValueEnforcer.notNull (aParticipantID, "ParticipantID");
    ValueEnforcer.notEmpty (sNewOwnerID, "NewOwnerID");
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("updateSMPServiceGroup (" +
                    aParticipantID.getURIEncoded () +
                    ", " +
                    sNewOwnerID +
                    ", " +
                    (StringHelper.hasText (sExtension) ? "with extension" : "without extension") +
                    ")");

    final JPAExecutionResult <EChange> ret;
    ret = doInTransaction ( () -> {
      // Check if the passed service group ID is already in use
      final EntityManager aEM = getEntityManager ();
      final DBServiceGroup aDBServiceGroup = aEM.find (DBServiceGroup.class, new DBServiceGroupID (aParticipantID));
      if (aDBServiceGroup == null)
        return EChange.UNCHANGED;

      EChange eChange = EChange.UNCHANGED;
      final DBOwnership aOldOwnership = aDBServiceGroup.getOwnership ();
      if (!aOldOwnership.getId ().getUsername ().equals (sNewOwnerID))
      {
        // Update ownership

        // Is new owner existing?
        final DBUser aNewUser = aEM.find (DBUser.class, sNewOwnerID);
        if (aNewUser == null)
          throw new IllegalStateException ("User '" + sNewOwnerID + "' does not exist!");

        aEM.remove (aOldOwnership);

        // The business did exist. So it must be owned by the passed user.
        final DBOwnershipID aDBOwnershipID = new DBOwnershipID (sNewOwnerID, aParticipantID);
        aDBServiceGroup.setOwnership (new DBOwnership (aDBOwnershipID, aNewUser, aDBServiceGroup));

        eChange = EChange.CHANGED;
      }

      // Simply update the extension
      if (!EqualsHelper.equals (aDBServiceGroup.getExtension (), sExtension))
        eChange = EChange.CHANGED;
      aDBServiceGroup.setExtension (sExtension);

      aEM.merge (aDBServiceGroup);
      return eChange;
    });

    if (ret.hasException ())
    {
      final Exception ex = ret.getException ();
      if (ex instanceof SMPServerException)
        throw (SMPServerException) ex;
      throw new SMPInternalErrorException ("Failed to update ServiceGroup '" + aParticipantID.getURIEncoded () + "'", ret.getException ());
    }

    final EChange eChange = ret.get ();
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("updateSMPServiceGroup succeeded. Change=" + eChange.isChanged ());

    final String sServiceGroupID = SMPServiceGroup.createSMPServiceGroupID (aParticipantID);

    // Callback only if something changed
    if (eChange.isChanged ())
    {
      AuditHelper.onAuditModifySuccess (SMPServiceGroup.OT, "all", sServiceGroupID, sNewOwnerID, sExtension);
      m_aCBs.forEach (x -> x.onSMPServiceGroupUpdated (aParticipantID));
    }
    else
      AuditHelper.onAuditModifyFailure (SMPServiceGroup.OT, "no-such-id", sServiceGroupID);

    return eChange;
  }

  @Nonnull
  public EChange deleteSMPServiceGroup (@Nonnull final IParticipantIdentifier aParticipantID) throws SMPServerException
  {
    ValueEnforcer.notNull (aParticipantID, "ParticipantID");
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("deleteSMPServiceGroup (" + aParticipantID.getURIEncoded () + ")");

    final IRegistrationHook aHook = RegistrationHookFactory.getInstance ();
    final MutableBoolean aDeletedServiceGroupInSML = new MutableBoolean (false);

    JPAExecutionResult <EChange> ret;
    ret = doInTransaction ( () -> {
      // Check if the service group is existing
      final EntityManager aEM = getEntityManager ();
      final DBServiceGroupID aDBServiceGroupID = new DBServiceGroupID (aParticipantID);
      final DBServiceGroup aDBServiceGroup = aEM.find (DBServiceGroup.class, aDBServiceGroupID);
      if (aDBServiceGroup == null)
        throw new SMPNotFoundException ("No such service group '" + aParticipantID.getURIEncoded () + "'");

      {
        // Delete in SML - and remember that
        // throws exception in case of error
        aHook.deleteServiceGroup (aParticipantID);
        aDeletedServiceGroupInSML.set (true);
      }

      aEM.remove (aDBServiceGroup);
      return EChange.CHANGED;
    });

    if (ret.isFailure ())
    {
      // Error writing to the DB
      if (aDeletedServiceGroupInSML.booleanValue ())
      {
        // Undo deletion in SML!
        try
        {
          aHook.undoDeleteServiceGroup (aParticipantID);
        }
        catch (final RegistrationHookException ex)
        {
          LOGGER.error ("Failed to undoDeleteServiceGroup (" + aParticipantID.getURIEncoded () + ")", ex);
        }
      }
    }

    if (ret.hasException ())
    {
      final Exception ex = ret.getException ();
      if (ex instanceof SMPServerException)
        throw (SMPServerException) ex;
      if (ex instanceof RegistrationHookException)
        throw new SMPSMLException ("Failed to delete '" + aParticipantID.getURIEncoded () + "' in SML", (RegistrationHookException) ex);
      throw new SMPInternalErrorException ("Failed to delete ServiceGroup '" + aParticipantID.getURIEncoded () + "'", ret.getException ());
    }

    final EChange eChange = ret.get ();
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("deleteSMPServiceGroup succeeded. Change=" + eChange.isChanged ());

    if (eChange.isChanged ())
    {
      AuditHelper.onAuditDeleteSuccess (SMPServiceGroup.OT, aParticipantID);
      m_aCBs.forEach (x -> x.onSMPServiceGroupDeleted (aParticipantID));
    }
    else
      AuditHelper.onAuditDeleteFailure (SMPServiceGroup.OT, "no-such-id", aParticipantID);

    return eChange;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPServiceGroup> getAllSMPServiceGroups ()
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("getAllSMPServiceGroups()");

    JPAExecutionResult <ICommonsList <ISMPServiceGroup>> ret;
    ret = doSelect ( () -> {
      final List <DBServiceGroup> aDBServiceGroups = getEntityManager ().createQuery ("SELECT p FROM DBServiceGroup p",
                                                                                      DBServiceGroup.class)
                                                                        .getResultList ();

      final ICommonsList <ISMPServiceGroup> aList = new CommonsArrayList <> ();
      for (final DBServiceGroup aDBServiceGroup : aDBServiceGroups)
      {
        final DBOwnership aDBOwnership = aDBServiceGroup.getOwnership ();
        if (aDBOwnership == null)
          LOGGER.error ("Service group " + aDBServiceGroup.getId ().getAsBusinessIdentifier ().getURIEncoded () + " has no owner");
        else
        {
          final SMPServiceGroup aServiceGroup = new SMPServiceGroup (aDBOwnership.getId ().getUsername (),
                                                                     aDBServiceGroup.getId ().getAsBusinessIdentifier (),
                                                                     aDBServiceGroup.getExtension ());
          aList.add (aServiceGroup);
        }
      }
      return aList;
    });
    if (ret.hasException ())
    {
      return new CommonsArrayList <> ();
    }
    return ret.get ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPServiceGroup> getAllSMPServiceGroupsOfOwner (@Nonnull final String sOwnerID)
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("getAllSMPServiceGroupsOfOwner(" + sOwnerID + ")");

    final JPAExecutionResult <ICommonsList <ISMPServiceGroup>> ret;
    ret = doSelect ( () -> {
      final List <DBServiceGroup> aDBServiceGroups = getEntityManager ().createQuery ("SELECT p FROM DBServiceGroup p WHERE p.ownership.user.userName = :user",
                                                                                      DBServiceGroup.class)
                                                                        .setParameter ("user", sOwnerID)
                                                                        .getResultList ();

      final ICommonsList <ISMPServiceGroup> aList = new CommonsArrayList <> ();
      for (final DBServiceGroup aDBServiceGroup : aDBServiceGroups)
      {
        aList.add (new SMPServiceGroup (sOwnerID, aDBServiceGroup.getId ().getAsBusinessIdentifier (), aDBServiceGroup.getExtension ()));
      }
      return aList;
    });
    if (ret.hasException ())
    {
      return new CommonsArrayList <> ();
    }
    return ret.get ();
  }

  @Nonnegative
  public long getSMPServiceGroupCountOfOwner (@Nonnull final String sOwnerID)
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("getSMPServiceGroupCountOfOwner(" + sOwnerID + ")");

    final JPAExecutionResult <Long> ret;
    ret = doSelect ( () -> {
      final long nCount = getSelectCountResult (getEntityManager ().createQuery ("SELECT COUNT(p) FROM DBOwnership p WHERE p.user.userName = :user",
                                                                                 DBOwnership.class)
                                                                   .setParameter ("user", sOwnerID));
      return Long.valueOf (nCount);
    });
    if (ret.hasException ())
    {
      return 0;
    }
    return ret.get ().longValue ();
  }

  @Nullable
  public SMPServiceGroup getSMPServiceGroupOfID (@Nullable final IParticipantIdentifier aParticipantID)
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("getSMPServiceGroupOfID(" + (aParticipantID == null ? "null" : aParticipantID.getURIEncoded ()) + ")");

    if (aParticipantID == null)
      return null;

    final JPAExecutionResult <SMPServiceGroup> ret;
    ret = doSelect ( () -> {
      final DBServiceGroup aDBServiceGroup = getEntityManager ().find (DBServiceGroup.class, new DBServiceGroupID (aParticipantID));
      if (aDBServiceGroup == null)
        return null;

      if (aDBServiceGroup.getOwnership () == null)
        return null;

      return new SMPServiceGroup (aDBServiceGroup.getOwnership ().getId ().getUsername (),
                                  aDBServiceGroup.getId ().getAsBusinessIdentifier (),
                                  aDBServiceGroup.getExtension ());
    });
    if (ret.hasException ())
    {
      return null;
    }
    return ret.get ();
  }

  public boolean containsSMPServiceGroupWithID (@Nullable final IParticipantIdentifier aParticipantID)
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("containsSMPServiceGroupWithID(" + (aParticipantID == null ? "null" : aParticipantID.getURIEncoded ()) + ")");

    if (aParticipantID == null)
      return false;

    final JPAExecutionResult <Boolean> ret;
    ret = doSelect ( () -> {
      // Disable caching here
      final ICommonsMap <String, Object> aProps = new CommonsHashMap <> ();
      aProps.put ("eclipselink.cache-usage", CacheUsage.DoNotCheckCache);
      final DBServiceGroup aDBServiceGroup = getEntityManager ().find (DBServiceGroup.class, new DBServiceGroupID (aParticipantID), aProps);
      return Boolean.valueOf (aDBServiceGroup != null);
    });
    if (ret.hasException ())
    {
      return false;
    }
    return ret.get ().booleanValue ();
  }

  @Nonnegative
  public long getSMPServiceGroupCount ()
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("getSMPServiceGroupCount()");

    final JPAExecutionResult <Long> ret;
    ret = doSelect ( () -> {
      final long nCount = getSelectCountResult (getEntityManager ().createQuery ("SELECT COUNT(p.id) FROM DBServiceGroup p"));
      return Long.valueOf (nCount);
    });
    if (ret.hasException ())
    {
      return 0;
    }
    return ret.get ().longValue ();
  }
}
