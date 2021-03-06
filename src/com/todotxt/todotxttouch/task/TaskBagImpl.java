/**
 *
 * Todo.txt Touch/src/com/todotxt/todotxttouch/task/TaskBagImpl.java
 *
 * Copyright (c) 2011 Tim Barlotta
 *
 * LICENSE:
 *
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).
 *
 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.
 *
 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Tim Barlotta <tim[at]barlotta[dot]net>
 * @author Tormod Haugen
 * @copyright 2011 Tim Barlotta
 */
package com.todotxt.todotxttouch.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.SharedPreferences;

import com.todotxt.todotxttouch.remote.RemoteClientManager;
import com.todotxt.todotxttouch.util.TaskIo;

/**
 * Implementation of the TaskBag interface
 * 
 * @author Tim Barlotta
 */
class TaskBagImpl implements TaskBag {
	private Preferences preferences;
	private final LocalTaskRepository localRepository;
	private final RemoteClientManager remoteClientManager;
	private ArrayList<Task> tasks = new ArrayList<Task>();

	public TaskBagImpl(Preferences preferences,
			LocalTaskRepository localRepository,
			RemoteClientManager remoteClientManager) {
		this.preferences = preferences;
		this.localRepository = localRepository;
		this.remoteClientManager = remoteClientManager;
	}

	public void updatePreferences(TaskBagImpl.Preferences preferences) {
		this.preferences = preferences;
	}

	@Override
	public void reload() {
		localRepository.init();
		this.tasks = localRepository.load();
	}

	@Override
	public int size() {
		return tasks.size();
	}

	@Override
	public List<Task> getTasks() {
		return getTasks(null, null);
	}

	@Override
	public List<Task> getTasks(Filter<Task> filter, Comparator<Task> comparator) {
		ArrayList<Task> localTasks = new ArrayList<Task>();
		if (filter != null) {
			for (Task t : tasks) {
				if (filter.apply(t)) {
					localTasks.add(t);
				}
			}
		} else {
			localTasks.addAll(tasks);
		}

		if (comparator == null) {
			comparator = Sort.PRIORITY_DESC.getComparator();
		}

		Collections.sort(localTasks, comparator);

		return localTasks;
	}

	@Override
	public void addAsTask(String input) {
		try {
			reload();
			Task task = new Task(tasks.size(), input,
					(preferences.isPrependDateEnabled() ? new Date() : null));
			tasks.add(task);
			localRepository.store(tasks);
		} catch (Exception e) {
			throw new TaskPersistException("An error occurred while adding {"
					+ input + "}", e);
		}
	}

	@Override
	public void update(Task task) {
		try {
			reload();
			Task found = TaskBagImpl.find(tasks, task);
			if (found != null) {
				task.copyInto(found);
				// Log.i(TAG, "copied into found {" + found + "}");
				localRepository.store(tasks);
			} else {
				throw new TaskPersistException("Task not found, not updated");
			}
		} catch (Exception e) {
			throw new TaskPersistException(
					"An error occurred while updating Task {" + task + "}", e);
		}
	}

	@Override
	public void delete(Task task) {
		try {
			reload();
			Task found = TaskBagImpl.find(tasks, task);
			if (found != null) {
				tasks.remove(found);
				localRepository.store(tasks);
			} else {
				throw new TaskPersistException("Task not found, not deleted");
			}
		} catch (Exception e) {
			throw new TaskPersistException(
					"An error occurred while deleting Task {" + task + "}", e);
		}
	}

	/* REMOTE APIS */
	@Override
	public void pushToRemote() {
		pushToRemote(false);
	}

	@Override
	public void pushToRemote(boolean overridePreference) {
		if (!this.preferences.isWorkOfflineEnabled() || overridePreference) {
			remoteClientManager.getRemoteClient().pushTodo(
					LocalFileTaskRepository.TODO_TXT_FILE);
		}
	}

	@Override
	public void pullFromRemote() {
		pullFromRemote(false);
	}

	@Override
	public void pullFromRemote(boolean overridePreference) {
		try {
			if (!this.preferences.isWorkOfflineEnabled() || overridePreference) {
				File remoteFile = remoteClientManager.getRemoteClient()
						.pullTodo();
				if (remoteFile != null && remoteFile.exists()) {
					ArrayList<Task> remoteTasks = TaskIo
							.loadTasksFromFile(remoteFile);
					localRepository.store(remoteTasks);
					reload();
				}
			}
		} catch (IOException e) {
			throw new TaskPersistException(
					"Error loading tasks from remote file", e);
		}
	}

	@Override
	public ArrayList<Priority> getPriorities() {
		// TODO cache this after reloads?
		Set<Priority> res = new HashSet<Priority>();
		for (Task item : tasks) {
			res.add(item.getPriority());
		}
		ArrayList<Priority> ret = new ArrayList<Priority>(res);
		Collections.sort(ret);
		return ret;
	}

	@Override
	public ArrayList<String> getContexts() {
		// TODO cache this after reloads?
		Set<String> res = new HashSet<String>();
		for (Task item : tasks) {
			res.addAll(item.getContexts());
		}
		ArrayList<String> ret = new ArrayList<String>(res);
		Collections.sort(ret);
		return ret;
	}

	@Override
	public ArrayList<String> getProjects() {
		// TODO cache this after reloads?
		Set<String> res = new HashSet<String>();
		for (Task item : tasks) {
			res.addAll(item.getProjects());
		}
		ArrayList<String> ret = new ArrayList<String>(res);
		Collections.sort(ret);
		return ret;
	}

	private static Task find(List<Task> tasks, Task task) {
		for (Task task2 : tasks) {
			if (task2.getText().equals(task.getOriginalText())
					&& task2.getPriority() == task.getOriginalPriority()) {
				return task2;
			}
		}
		return null;
	}

	public static class Preferences {
		private final SharedPreferences sharedPreferences;

		public Preferences(SharedPreferences sharedPreferences) {
			this.sharedPreferences = sharedPreferences;
		}

		public boolean isUseWindowsLineBreaksEnabled() {
			return sharedPreferences.getBoolean("linebreakspref", false);
		}

		public boolean isPrependDateEnabled() {
			return sharedPreferences.getBoolean("todotxtprependdate", false);
		}

		public boolean isWorkOfflineEnabled() {
			return sharedPreferences.getBoolean("workofflinepref", false);
		}
	}
}
