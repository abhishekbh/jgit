/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.junit.Test;

public class CloneCommandTest extends RepositoryTestCase {

	private Git git;

	private TestRepository<Repository> tr;

	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<Repository>(db);

		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Second commit").call();
		RevBlob blob = tr.blob("blob-not-in-master-branch");
		git.tag().setName("tag-for-blob").setObjectId(blob).call();
	}

	@Test
	public void testCloneRepository() {
		try {
			File directory = createTempDirectory("testCloneRepository");
			CloneCommand command = Git.cloneRepository();
			command.setDirectory(directory);
			command.setURI("file://"
					+ git.getRepository().getWorkTree().getPath());
			Git git2 = command.call();
			addRepoToClose(git2.getRepository());
			assertNotNull(git2);
			ObjectId id = git2.getRepository().resolve("tag-for-blob");
			assertNotNull(id);
			assertEquals(git2.getRepository().getFullBranch(),
					"refs/heads/test");
			assertEquals(
					"origin",
					git2.getRepository()
							.getConfig()
							.getString(ConfigConstants.CONFIG_BRANCH_SECTION,
									"test", ConfigConstants.CONFIG_KEY_REMOTE));
			assertEquals(
					"refs/heads/test",
					git2.getRepository()
							.getConfig()
							.getString(ConfigConstants.CONFIG_BRANCH_SECTION,
									"test", ConfigConstants.CONFIG_KEY_MERGE));
			assertEquals(2, git2.branchList().setListMode(ListMode.REMOTE)
					.call().size());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testCloneRepositoryWithBranch() {
		try {
			File directory = createTempDirectory("testCloneRepositoryWithBranch");
			CloneCommand command = Git.cloneRepository();
			command.setBranch("refs/heads/master");
			command.setDirectory(directory);
			command.setURI("file://"
					+ git.getRepository().getWorkTree().getPath());
			Git git2 = command.call();
			addRepoToClose(git2.getRepository());

			assertNotNull(git2);
			assertEquals(git2.getRepository().getFullBranch(),
					"refs/heads/master");
			assertEquals(
					"refs/heads/master, refs/remotes/origin/master, refs/remotes/origin/test",
					allRefNames(git2.branchList().setListMode(ListMode.ALL)
							.call()));

			// Same thing, but now without checkout
			directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
			command = Git.cloneRepository();
			command.setBranch("refs/heads/master");
			command.setDirectory(directory);
			command.setURI("file://"
					+ git.getRepository().getWorkTree().getPath());
			command.setNoCheckout(true);
			git2 = command.call();
			addRepoToClose(git2.getRepository());

			assertNotNull(git2);
			assertEquals(git2.getRepository().getFullBranch(),
					"refs/heads/master");
			assertEquals(
					"refs/remotes/origin/master, refs/remotes/origin/test",
					allRefNames(git2.branchList().setListMode(ListMode.ALL)
							.call()));

			// Same thing, but now test with bare repo
			directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
			command = Git.cloneRepository();
			command.setBranch("refs/heads/master");
			command.setDirectory(directory);
			command.setURI("file://"
					+ git.getRepository().getWorkTree().getPath());
			command.setBare(true);
			git2 = command.call();
			addRepoToClose(git2.getRepository());

			assertNotNull(git2);
			assertEquals(git2.getRepository().getFullBranch(),
					"refs/heads/master");
			assertEquals("refs/heads/master, refs/heads/test", allRefNames(git2
					.branchList().setListMode(ListMode.ALL).call()));
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testCloneRepositoryOnlyOneBranch() {
		try {
			File directory = createTempDirectory("testCloneRepositoryWithBranch");
			CloneCommand command = Git.cloneRepository();
			command.setBranch("refs/heads/master");
			command.setBranchesToClone(Collections
					.singletonList("refs/heads/master"));
			command.setDirectory(directory);
			command.setURI("file://"
					+ git.getRepository().getWorkTree().getPath());
			Git git2 = command.call();
			addRepoToClose(git2.getRepository());
			assertNotNull(git2);
			assertEquals(git2.getRepository().getFullBranch(),
					"refs/heads/master");
			assertEquals("refs/remotes/origin/master",
					allRefNames(git2.branchList()
					.setListMode(ListMode.REMOTE).call()));

			// Same thing, but now test with bare repo
			directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
			command = Git.cloneRepository();
			command.setBranch("refs/heads/master");
			command.setBranchesToClone(Collections
					.singletonList("refs/heads/master"));
			command.setDirectory(directory);
			command.setURI("file://"
					+ git.getRepository().getWorkTree().getPath());
			command.setBare(true);
			git2 = command.call();
			addRepoToClose(git2.getRepository());
			assertNotNull(git2);
			assertEquals(git2.getRepository().getFullBranch(),
					"refs/heads/master");
			assertEquals("refs/heads/master", allRefNames(git2
					.branchList().setListMode(ListMode.ALL).call()));
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	public static String allRefNames(List<Ref> refs) {
		StringBuilder sb = new StringBuilder();
		for (Ref f : refs) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(f.getName());
		}
		return sb.toString();
	}

	public static File createTempDirectory(String name) throws IOException {
		final File temp;
		temp = File.createTempFile(name, Long.toString(System.nanoTime()));

		if (!(temp.delete())) {
			throw new IOException("Could not delete temp file: "
					+ temp.getAbsolutePath());
		}

		if (!(temp.mkdir())) {
			throw new IOException("Could not create temp directory: "
					+ temp.getAbsolutePath());
		}
		return temp;
	}

}
