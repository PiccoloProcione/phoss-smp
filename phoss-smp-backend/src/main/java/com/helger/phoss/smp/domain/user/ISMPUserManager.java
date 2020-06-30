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
package com.helger.phoss.smp.domain.user;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.state.EChange;
import com.helger.commons.state.ESuccess;
import com.helger.http.basicauth.BasicAuthClientCredentials;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.phoss.smp.exception.SMPServerException;

/**
 * Abstraction interface for the user management depending on the used backend.
 *
 * @author PEPPOL.AT, BRZ, Philip Helger
 */
public interface ISMPUserManager extends ISMPUserProvider
{
  /**
   * @return <code>true</code> for SQL, <code>false</code> for XML. If this
   *         method returns <code>true</code> all user objects must implement
   *         {@link ISMPUserEditable}!
   */
  boolean isSpecialUserManagementNeeded ();

  @Nonnull
  ESuccess createUser (@Nonnull String sUserName, @Nonnull String sPassword);

  @Nonnull
  ESuccess updateUser (@Nonnull String sUserName, @Nonnull String sPassword);

  @Nonnull
  EChange deleteUser (@Nullable String sUserName);

  /**
   * @return The number of contained user. Always &ge; 0.
   */
  @Nonnegative
  long getUserCount ();

  @Nonnull
  @ReturnsMutableCopy
  ICommonsList <ISMPUser> getAllUsers ();

  /**
   * Check if an SMP user matching the user name of the BasicAuth credentials
   * exists, and that the passwords match. So this method verifies that the
   * BasicAuth credentials are valid.
   *
   * @param aCredentials
   *        The credentials to be validated. May not be <code>null</code>.
   * @return The matching non-<code>null</code> {@link ISMPUser}.
   * @throws SMPServerException
   *         If no user matching the passed user name is present or if the
   *         password in the credentials does not match the stored password
   *         (hash).
   */
  @Nonnull
  ISMPUser validateUserCredentials (@Nonnull BasicAuthClientCredentials aCredentials) throws SMPServerException;

  /**
   * Verify that the passed service group is owned by the user specified in the
   * credentials. This is always called directly after
   * {@link #validateUserCredentials(BasicAuthClientCredentials)}.
   *
   * @param aServiceGroupID
   *        The service group to be verified
   * @param aCurrentUser
   *        The user to verify.
   * @throws SMPServerException
   *         <code>SMPNotFoundException</code> If the passed service group does
   *         not exist on this SMP. <code>SMPUnauthorizedException</code> If the
   *         participant identifier is not owned by the user specified in the
   *         credentials
   */
  void verifyOwnership (@Nonnull IParticipantIdentifier aServiceGroupID, @Nonnull ISMPUser aCurrentUser) throws SMPServerException;
}
