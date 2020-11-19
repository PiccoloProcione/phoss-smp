/**
 * Copyright (C) 2014-2020 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phoss.smp.ui.secure;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.helger.html.hc.IHCNode;
import com.helger.html.hc.html.tabular.HCRow;
import com.helger.html.hc.html.tabular.HCTable;
import com.helger.html.hc.impl.HCNodeList;
import com.helger.phoss.smp.nicename.NiceNameEntry;
import com.helger.phoss.smp.nicename.NiceNameHandler;
import com.helger.phoss.smp.ui.AbstractSMPWebPage;
import com.helger.photon.bootstrap4.nav.BootstrapTabBox;
import com.helger.photon.bootstrap4.uictrls.datatables.BootstrapDataTables;
import com.helger.photon.core.EPhotonCoreText;
import com.helger.photon.uicore.page.WebPageExecutionContext;
import com.helger.photon.uictrls.datatables.column.DTCol;

/**
 * A read-only page that shows all the applied identifier mappings.
 *
 * @author Philip Helger
 * @since 5.3.2
 */
public final class PageSecureSMPIdentifierMappings extends AbstractSMPWebPage
{
  public PageSecureSMPIdentifierMappings (@Nonnull @Nonempty final String sID)
  {
    super (sID, "Identifier Mappings");
  }

  @Nonnull
  private IHCNode _createList (@Nonnull final WebPageExecutionContext aWPEC,
                               @Nonnull final ICommonsOrderedMap <String, NiceNameEntry> aEntries,
                               @Nonnull final String sSuffix)
  {
    final Locale aDisplayLocale = aWPEC.getDisplayLocale ();
    final HCTable aTable = new HCTable (new DTCol ("ID"), new DTCol ("Name"), new DTCol ("Deprecated?")).setID (getID () + sSuffix);
    for (final Map.Entry <String, NiceNameEntry> aEntry : aEntries.entrySet ())
    {
      final HCRow aRow = aTable.addBodyRow ();
      aRow.addCell (aEntry.getKey ());
      aRow.addCell (aEntry.getValue ().getName ());
      aRow.addCell (EPhotonCoreText.getYesOrNo (aEntry.getValue ().isDeprecated (), aDisplayLocale));
    }
    return new HCNodeList ().addChild (aTable).addChild (BootstrapDataTables.createDefaultDataTables (aWPEC, aTable));
  }

  @Override
  protected void fillContent (@Nonnull final WebPageExecutionContext aWPEC)
  {
    final HCNodeList aNodeList = aWPEC.getNodeList ();

    final BootstrapTabBox aTabBox = aNodeList.addAndReturnChild (new BootstrapTabBox ());
    aTabBox.addTab ("doctypes", "Document Types", _createList (aWPEC, NiceNameHandler.getAllDocumentTypeMappings (), "doctypes"));
    aTabBox.addTab ("procs", "Processes", _createList (aWPEC, NiceNameHandler.getAllProcessMappings (), "procs"));
  }
}
