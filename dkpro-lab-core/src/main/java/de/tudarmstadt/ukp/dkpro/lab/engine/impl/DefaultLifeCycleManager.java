/*******************************************************************************
 * Copyright 2011
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *   
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.lab.engine.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataAccessResourceFailureException;

import de.tudarmstadt.ukp.dkpro.lab.engine.LifeCycleException;
import de.tudarmstadt.ukp.dkpro.lab.engine.LifeCycleManager;
import de.tudarmstadt.ukp.dkpro.lab.engine.TaskContext;
import de.tudarmstadt.ukp.dkpro.lab.reporting.Report;
import de.tudarmstadt.ukp.dkpro.lab.task.Task;
import de.tudarmstadt.ukp.dkpro.lab.task.TaskContextMetadata;

public class DefaultLifeCycleManager
	implements LifeCycleManager
{
	@Override
	public void initialize(TaskContext aContext,
			Task aConfiguration)
		throws LifeCycleException
	{
		try {
			aConfiguration.persist(aContext);
		}
		catch (IOException e) {
			throw new LifeCycleException(e);
		}

		aContext.message("Initialized task ["+aConfiguration.getType()+"]");
	}

	@Override
	public void begin(TaskContext aContext,
			Task aConfiguration)
	{
		for (int g = 0; g < 3; g++) {
			System.gc();
		}
		aContext.getMetadata().setStart(System.currentTimeMillis());
		aContext.message("Starting task ["+aConfiguration.getType()+"]");
	}

	@Override
	public void complete(TaskContext aContext, Task aConfiguration)
		throws LifeCycleException
	{
		aContext.getMetadata().setEnd(System.currentTimeMillis());
		aContext.message("Completing task ["+aConfiguration.getType()+"]");
		aContext.message("Running reports for task ["+aConfiguration.getType()+"]");
		List<Class<? extends Report>> reports = new ArrayList<Class<? extends Report>>(
				aConfiguration.getReports());
		Collections.sort(reports, new Comparator<Class<?>>()
		{
			@Override
			public int compare(Class<?> aO1, Class<?> aO2)
			{
				return aO1.getName().compareTo(aO2.getName());
			}
		});
		int i = 1;
		for (Class<? extends Report> reportClass : reports) {
			for (int g = 0; g < 3; g++) {
				System.gc();
			}
			try {
				aContext.message("Starting report [" + reportClass.getName() + "] (" + i + "/"
						+ reports.size() + ")");
				Report report = reportClass.newInstance();
				report.setContext(aContext);
				report.execute();
				aContext.message("Report complete [" + reportClass.getName() + "] (" + i + "/"
						+ reports.size() + ")");
			}
			catch (Exception e) {
				aContext.error("Report failed [" + reportClass.getName() + "] (" + i + "/"
						+ reports.size() + ")", e);
				throw new LifeCycleException(e);
			}
			finally {
				i++;
			}
		}
		
		// This is a critical file as it marks if a task has completed successfully or not. If
		// this file cannot be created properly, e.g. because the disk is full, then there will be
		// subsequent and hard to debug errors. Thus, if the file cannot be created properly, any
		// potentially incomplete version of this file has to be deleted.
		try {
			aContext.storeBinary(TaskContextMetadata.METADATA_KEY, aContext.getMetadata());
		}
		catch (Throwable e) {
			aContext.getStorageService().delete(aContext.getId(), TaskContextMetadata.METADATA_KEY);
			throw new LifeCycleException("Unable to write [" + TaskContextMetadata.METADATA_KEY
					+ "] to mark context as complete.", e);
		}
		aContext.message("Completed task ["+aConfiguration.getType()+"]");
	}

	@Override
	public void fail(TaskContext aContext, Task aConfiguration, Throwable aCause)
		throws LifeCycleException
	{
		try {
			aContext.getStorageService().delete(aContext.getId());
		}
		catch (DataAccessResourceFailureException e) {
			aContext.error("Unable to clean up context after failure. Some data may remain in " +
					"the context.", e);
		}
		aContext.error("Task failed ["+aConfiguration.getType()+"]", aCause);
	}

	@Override
	public void destroy(TaskContext aContext)
	{
		aContext.message("Shut down task");
	}
}
