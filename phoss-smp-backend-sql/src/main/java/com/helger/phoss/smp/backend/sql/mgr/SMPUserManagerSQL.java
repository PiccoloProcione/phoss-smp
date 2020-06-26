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

import java.util.Collection;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;

import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.state.EChange;
import com.helger.commons.state.ESuccess;
import com.helger.commons.string.StringHelper;
import com.helger.db.jpa.JPAExecutionResult;
import com.helger.http.basicauth.BasicAuthClientCredentials;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.phoss.smp.backend.sql.AbstractSMPJPAEnabledManager;
import com.helger.phoss.smp.backend.sql.model.DBOwnership;
import com.helger.phoss.smp.backend.sql.model.DBOwnershipID;
import com.helger.phoss.smp.backend.sql.model.DBServiceGroup;
import com.helger.phoss.smp.backend.sql.model.DBServiceGroupID;
import com.helger.phoss.smp.backend.sql.model.DBUser;
import com.helger.phoss.smp.domain.user.ISMPUser;
import com.helger.phoss.smp.domain.user.ISMPUserManager;
import com.helger.phoss.smp.exception.SMPNotFoundException;
import com.helger.phoss.smp.exception.SMPServerException;
import com.helger.phoss.smp.exception.SMPUnauthorizedException;
import com.helger.phoss.smp.exception.SMPUnknownUserException;

/**
 * A EclipseLink based implementation of the {@link ISMPUserManager} interface.
 *
 * @author PEPPOL.AT, BRZ, Philip Helger
 */
public final class SMPUserManagerSQL extends AbstractSMPJPAEnabledManager implements ISMPUserManager
{
  public SMPUserManagerSQL ()
  {}

  public boolean isSpecialUserManagementNeeded ()
  {
    return true;
  }

  @Nonnull
  public ESuccess createUser (@Nonnull final String sUserName, @Nonnull final String sPassword)
  {
    JPAExecutionResult <?> ret;
    ret = doInTransaction ( () -> {
      final DBUser aDBUser = new DBUser (sUserName, sPassword);
      getEntityManager ().persist (aDBUser);
    });
    if (ret.hasException ())
    {
      return ESuccess.FAILURE;
    }
    return ESuccess.SUCCESS;
  }

  @Nonnull
  public ESuccess updateUser (@Nonnull final String sUserName, @Nonnull final String sPassword)
  {
    JPAExecutionResult <?> ret;
    ret = doInTransaction ( () -> {
      final DBUser aDBUser = getEntityManager ().find (DBUser.class, sUserName);
      if (aDBUser != null)
      {
        aDBUser.setPassword (sPassword);
        getEntityManager ().merge (aDBUser);
      }
    });
    if (ret.hasException ())
    {
      return ESuccess.FAILURE;
    }
    return ESuccess.SUCCESS;
  }

  @Nonnull
  public EChange deleteUser (@Nullable final String sUserName)
  {
    EChange eChange = EChange.CHANGED;
    if (StringHelper.hasText (sUserName))
    {
      JPAExecutionResult <EChange> ret;
      ret = doInTransaction ( () -> {
        final EntityManager aEM = getEntityManager ();
        final DBUser aDBUser = aEM.find (DBUser.class, sUserName);
        if (aDBUser == null)
          return EChange.UNCHANGED;
        aEM.remove (aDBUser);
        return EChange.CHANGED;
      });
      if (ret.hasException ())
      {
        return EChange.UNCHANGED;
      }
      eChange = ret.get ();
    }
    return eChange;
  }

  @Nonnegative
  public long getUserCount ()
  {
    JPAExecutionResult <Long> ret;
    ret = doSelect ( () -> {
      final long nCount = getSelectCountResult (getEntityManager ().createQuery ("SELECT COUNT(p) FROM DBUser p"));
      return Long.valueOf (nCount);
    });
    if (ret.hasException ())
    {
      return 0;
    }
    return ret.get ().longValue ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPUser> getAllUsers ()
  {
    JPAExecutionResult <Collection <DBUser>> ret;
    ret = doSelect ( () -> getEntityManager ().createQuery ("SELECT p FROM DBUser p", DBUser.class).getResultList ());
    if (ret.hasException ())
    {
      return new CommonsArrayList <> ();
    }
    return new CommonsArrayList <> (ret.get ());
  }

  @Nullable
  public DBUser getUserOfID (@Nullable final String sID)
  {
    if (StringHelper.hasNoText (sID))
      return null;

    JPAExecutionResult <DBUser> ret;
    ret = doSelect ( () -> getEntityManager ().find (DBUser.class, sID));
    if (ret.hasException ())
    {
      return null;
    }
    return ret.get ();
  }

  @Nonnull
  public DBUser validateUserCredentials (@Nonnull final BasicAuthClientCredentials aCredentials) throws SMPServerException
  {
    JPAExecutionResult <DBUser> ret;
    ret = doSelect ( () -> {
      final String sUserName = aCredentials.getUserName ();
      final DBUser aDBUser = getEntityManager ().find (DBUser.class, sUserName);

      // Check that the user exists
      if (aDBUser == null)
        throw new SMPUnknownUserException (sUserName);

      // Check that the password is correct
      if (!aDBUser.getPassword ().equals (aCredentials.getPassword ()))
        throw new SMPUnauthorizedException ("Illegal password for user '" + sUserName + "'");

      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Verified credentials of user '" + sUserName + "' successfully");
      return aDBUser;
    });
    if (ret.hasException ())
    {
      final Exception ex = ret.getException ();
      if (ex instanceof SMPServerException)
        throw (SMPServerException) ex;
      throw new SMPServerException ("Database error", ex);
    }

    return ret.get ();
  }

  public void verifyOwnership (@Nonnull final IParticipantIdentifier aServiceGroupID,
                               @Nonnull final ISMPUser aCredentials) throws SMPServerException
  {
    // Resolve service group
    // to throw a 404 if a service group does not exist
    final DBServiceGroup aServiceGroup = getEntityManager ().find (DBServiceGroup.class, new DBServiceGroupID (aServiceGroupID));
    if (aServiceGroup == null)
    {
      throw new SMPNotFoundException ("Service group " + aServiceGroupID.getURIEncoded () + " does not exist");
    }

    final DBOwnershipID aOwnershipID = new DBOwnershipID (aCredentials.getID (), aServiceGroupID);
    final DBOwnership aOwnership = getEntityManager ().find (DBOwnership.class, aOwnershipID);
    if (aOwnership == null)
    {
      throw new SMPUnauthorizedException ("User '" + aCredentials.getUserName () + "' does not own " + aServiceGroupID.getURIEncoded ());
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Verified service group ID " +
                    aServiceGroupID.getURIEncoded () +
                    " is owned by user '" +
                    aCredentials.getUserName () +
                    "'");
  }
}
