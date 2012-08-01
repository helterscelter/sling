/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.installer.core.impl.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.sling.installer.api.tasks.InstallTask;
import org.apache.sling.installer.api.tasks.InstallationContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.service.packageadmin.ExportedPackage;

/**
 * Refresh a set of bundles.
 */
public class RefreshBundlesTask
    extends AbstractBundleTask
    implements FrameworkListener{

    private static final String REFRESH_PACKAGES_ORDER = "60-";

    /** Max time allowed to refresh packages */
    private static final int MAX_REFRESH_PACKAGES_WAIT_SECONDS = 90;

    /** Counter for package refresh events. */
    private volatile long refreshEventCount;

    /** Global set of bundles to refresh. */
    private static final Set<Long> BUNDLE_IDS = new HashSet<Long>();

    public static void markBundleForRefresh(final InstallationContext ctx,
                    final TaskSupport btc,
                    final Bundle bundle) {
        synchronized ( BUNDLE_IDS ) {
            BUNDLE_IDS.add(bundle.getBundleId());
            ctx.addTaskToCurrentCycle(new RefreshBundlesTask(btc));
        }
    }

    public RefreshBundlesTask(final TaskSupport btc) {
	    super(null, btc);
	}

	@Override
	public String getSortKey() {
		return REFRESH_PACKAGES_ORDER;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	private boolean isExportingLogApi(final Bundle bundle) {
	    final ExportedPackage[] pcks = this.getPackageAdmin().getExportedPackages(bundle);
	    if ( pcks != null ) {
	        for(final ExportedPackage pak : pcks ) {
	            if ( pak.getName().equals("org.slf4j") || pak.getName().equals("javax.servlet.http")) {
	                return true;
	            }
	        }
	    }
	    return false;
	}

    /**
     * @see org.apache.sling.installer.api.tasks.InstallTask#execute(org.apache.sling.installer.api.tasks.InstallationContext)
     */
    public void execute(final InstallationContext ctx) {
        boolean requireAsyncRefresh = false;
        final List<Bundle> bundles = new ArrayList<Bundle>();
        synchronized ( BUNDLE_IDS ) {
            for(final Long id : BUNDLE_IDS) {
                final Bundle b = this.getBundleContext().getBundle(id);
                if ( b != null ) {
                    getLogger().debug("Will refresh bundle {}", b);
                    bundles.add(b);
                    if ( b.getBundleId() == this.getBundleContext().getBundle().getBundleId() ) {
                        requireAsyncRefresh = true;
                    } else if ( this.isExportingLogApi(b) ) {
                        requireAsyncRefresh = true;
                    }
                } else {
                    getLogger().debug("Unable to refresh bundle {} - already gone.", id);
                }
            }
            BUNDLE_IDS.clear();
        }
        if ( bundles.size() > 0 ) {
            if ( requireAsyncRefresh ) {
                ctx.log("Async refreshing of {} bundles: {} required", bundles.size(), bundles);
                ctx.addTaskToCurrentCycle(new AsyncRefreshBundlesTask(bundles));
                return;
            }
            ctx.log("Refreshing {} bundles: {}", bundles.size(), bundles);
            this.refreshEventCount = -1;
            this.getBundleContext().addFrameworkListener(this);
            try {
                this.refreshEventCount = 0;
                this.getPackageAdmin().refreshPackages(bundles.toArray(new Bundle[bundles.size()]));
                final long start = System.currentTimeMillis();
                do {
                    synchronized ( this ) {
                        try {
                            ctx.log("Waiting up to {} seconds for bundles refresh", MAX_REFRESH_PACKAGES_WAIT_SECONDS);
                            this.wait(MAX_REFRESH_PACKAGES_WAIT_SECONDS * 1000);
                        } catch (final InterruptedException ignore) {
                            // ignore
                        }
                        if ( start + MAX_REFRESH_PACKAGES_WAIT_SECONDS * 1000 < System.currentTimeMillis() ) {
                            this.getLogger().warn("No FrameworkEvent.PACKAGES_REFRESHED event received within {}"
                                            + " seconds after refresh, aborting wait.",
                                            MAX_REFRESH_PACKAGES_WAIT_SECONDS);
                            this.refreshEventCount++;
                        }
                    }
                } while ( this.refreshEventCount < 1);
            } finally {
                this.getBundleContext().removeFrameworkListener(this);
            }
            ctx.log("Done refreshing {} bundles", bundles.size());
        }
	}

    /**
     * @see org.osgi.framework.FrameworkListener#frameworkEvent(org.osgi.framework.FrameworkEvent)
     */
    public void frameworkEvent(final FrameworkEvent event) {
        if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
            this.getLogger().debug("FrameworkEvent.PACKAGES_REFRESHED");
            synchronized (this) {
                this.refreshEventCount++;
                this.notify();
            }
        }
    }

    private class AsyncRefreshBundlesTask extends InstallTask {

        private final List<Bundle> bundles;

        public AsyncRefreshBundlesTask(final List<Bundle> bundles) {
            super(null);
            this.bundles = bundles;
        }

        @Override
        public void execute(final InstallationContext ctx) {
            ctx.log("Refreshing {} bundles: {}", bundles.size(), bundles);
            RefreshBundlesTask.this.getPackageAdmin().refreshPackages(bundles.toArray(new Bundle[bundles.size()]));
            ctx.log("Done refreshing {} bundles", bundles.size());
        }

        @Override
        public String getSortKey() {
            return REFRESH_PACKAGES_ORDER;
        }

        @Override
        public boolean isAsynchronousTask() {
            return true;
        }
    }
}