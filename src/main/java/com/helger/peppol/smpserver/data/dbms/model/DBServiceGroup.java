/**
 * Copyright (C) 2015 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Version: MPL 1.1/EUPL 1.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at:
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Copyright The PEPPOL project (http://www.peppol.eu)
 *
 * Alternatively, the contents of this file may be used under the
 * terms of the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL
 * (the "Licence"); You may not use this work except in compliance
 * with the Licence.
 * You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * If you wish to allow use of your version of this file only
 * under the terms of the EUPL License and not to allow others to use
 * your version of this file under the MPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and
 * other provisions required by the EUPL License. If you do not delete
 * the provisions above, a recipient may use your version of this file
 * under either the MPL or the EUPL License.
 */
package com.helger.peppol.smpserver.data.dbms.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.helger.peppol.smp.ExtensionType;
import com.helger.peppol.smp.SMPExtensionConverter;

/**
 * ServiceGroup generated by hbm2java
 * 
 * @author PEPPOL.AT, BRZ, Philip Helger
 */
@Entity
@Table (name = "smp_service_group")
public class DBServiceGroup implements Serializable
{
  private DBServiceGroupID m_aID;
  private String m_sExtension;
  private Set <DBOwnership> m_aOwnerships = new HashSet <DBOwnership> ();
  private Set <DBServiceMetadata> m_aServiceMetadatas = new HashSet <DBServiceMetadata> ();

  public DBServiceGroup ()
  {}

  public DBServiceGroup (final DBServiceGroupID aID)
  {
    m_aID = aID;
  }

  public DBServiceGroup (final DBServiceGroupID aID,
                         final String sExtension,
                         final Set <DBOwnership> aOwnerships,
                         final Set <DBServiceMetadata> aServiceMetadatas)
  {
    m_aID = aID;
    m_sExtension = sExtension;
    m_aOwnerships = aOwnerships;
    m_aServiceMetadatas = aServiceMetadatas;
  }

  @EmbeddedId
  public DBServiceGroupID getId ()
  {
    return m_aID;
  }

  public void setId (final DBServiceGroupID aID)
  {
    m_aID = aID;
  }

  @Lob
  @Column (name = "extension", length = 65535)
  public String getExtension ()
  {
    return m_sExtension;
  }

  public void setExtension (@Nullable final String sExtension)
  {
    m_sExtension = sExtension;
  }

  @Transient
  public void setExtension (@Nullable final ExtensionType aExtension)
  {
    setExtension (SMPExtensionConverter.convertToString (aExtension));
  }

  @OneToMany (fetch = FetchType.LAZY, mappedBy = "serviceGroup", cascade = { CascadeType.ALL })
  public Set <DBOwnership> getOwnerships ()
  {
    return m_aOwnerships;
  }

  public void setOwnerships (final Set <DBOwnership> aOwnerships)
  {
    m_aOwnerships = aOwnerships;
  }

  @OneToMany (fetch = FetchType.LAZY, mappedBy = "serviceGroup", cascade = { CascadeType.ALL })
  public Set <DBServiceMetadata> getServiceMetadatas ()
  {
    return m_aServiceMetadatas;
  }

  public void setServiceMetadatas (final Set <DBServiceMetadata> aServiceMetadatas)
  {
    m_aServiceMetadatas = aServiceMetadatas;
  }
}
