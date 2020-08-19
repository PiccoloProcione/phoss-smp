/**
 * Copyright (C) 2015-2020 Philip Helger (www.helger.com)
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
package com.helger.phoss.smp.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.UsedViaReflection;
import com.helger.commons.callback.IThrowingRunnable;
import com.helger.commons.concurrent.BasicThreadFactory;
import com.helger.commons.concurrent.ExecutorServiceHelper;
import com.helger.commons.functional.IThrowingSupplier;
import com.helger.scope.IScope;
import com.helger.scope.singleton.AbstractGlobalSingleton;

/**
 * Asynchronous worker pool that handles stuff that runs in the background.
 *
 * @author Philip Helger
 */
public class SMPWorkerPool extends AbstractGlobalSingleton
{
  private static final Logger LOGGER = LoggerFactory.getLogger (SMPWorkerPool.class);

  private final ExecutorService m_aES;

  @Deprecated
  @UsedViaReflection
  public SMPWorkerPool ()
  {
    this (Runtime.getRuntime ().availableProcessors () * 2);
  }

  public SMPWorkerPool (@Nonnegative final int nThreadPoolSize)
  {
    this (Executors.newFixedThreadPool (nThreadPoolSize,
                                        new BasicThreadFactory.Builder ().setDaemon (true).setNamingPattern ("smp-worker-%d").build ()));
  }

  public SMPWorkerPool (@Nonnull final ExecutorService aES)
  {
    ValueEnforcer.notNull (aES, "ExecutorService");
    m_aES = aES;
  }

  @Nonnull
  public static SMPWorkerPool getInstance ()
  {
    return getGlobalSingleton (SMPWorkerPool.class);
  }

  @Override
  protected void onDestroy (@Nonnull final IScope aScopeInDestruction) throws Exception
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("SMP worker pool about to be closed");
    ExecutorServiceHelper.shutdownAndWaitUntilAllTasksAreFinished (m_aES);
    if (LOGGER.isInfoEnabled ())
      LOGGER.info ("SMP worker pool was closed!");
  }

  @Nonnull
  public CompletableFuture <Void> run (@Nonnull final IThrowingRunnable <? extends Exception> aRunnable)
  {
    return CompletableFuture.runAsync ( () -> {
      try
      {
        aRunnable.run ();
      }
      catch (final Exception ex)
      {
        LOGGER.error ("Error running SMP runner " + aRunnable, ex);
      }
    }, m_aES);
  }

  @Nonnull
  public <T> CompletableFuture <T> supply (@Nonnull final IThrowingSupplier <T, ? extends Exception> aSupplier)
  {
    return CompletableFuture.supplyAsync ( () -> {
      try
      {
        return aSupplier.get ();
      }
      catch (final Exception ex)
      {
        LOGGER.error ("Error running SMP supplier " + aSupplier, ex);
        return null;
      }
    }, m_aES);
  }
}
