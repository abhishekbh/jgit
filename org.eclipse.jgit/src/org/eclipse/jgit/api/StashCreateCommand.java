/*
 * Copyright (C) 2011, Abhishek Bhatnagar <abhatnag@redhat.com>
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FileUtils;

/**
 * Stash the changes in a dirty working directory away
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 */
public class StashCreateCommand extends GitCommand<RevCommit> {
	/**
	 * parents this commit should have. The current HEAD will be in this list
	 * and also all commits mentioned in .git/MERGE_HEAD
	 */
	private PersonIdent author = new PersonIdent("Bob Sacamano",
			"bsacamano@seinfeld.com");

	private PersonIdent committer = new PersonIdent("Test User",
			"test@redhat.com");

	/**
	 * @param repo
	 */
	protected StashCreateCommand(Repository repo) {
		super(repo);
		final Map<String, String> env = cloneEnv();
		putPersonIdent(env, "AUTHOR", author);
		putPersonIdent(env, "COMMITTER", committer);
	}

	private static void putPersonIdent(final Map<String, String> env,
			final String type, final PersonIdent who) {
		final String ident = who.toExternalString();
		final String date = ident.substring(ident.indexOf("> ") + 2);
		env.put("GIT_" + type + "_NAME", who.getName());
		env.put("GIT_" + type + "_EMAIL", who.getEmailAddress());
		env.put("GIT_" + type + "_DATE", date);
	}

	private RevCommit checkoutCurrentHead(DirCache dc) throws IOException,
			NoHeadException, JGitInternalException {
		ObjectId headTree = repo.resolve(Constants.HEAD + "^{tree}");

		System.out.println(author);
		System.out.println(committer);
		try {
			Thread.sleep(10000000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (headTree == null)
			throw new NoHeadException(
					JGitText.get().cannotRebaseWithoutCurrentHead);
		// DirCache dc = repo.lockDirCache();
		try {
			DirCacheCheckout dco = new DirCacheCheckout(repo, dc, headTree);
			dco.setFailOnConflict(false);
			boolean needsDeleteFiles = dco.checkout();
			if (needsDeleteFiles) {
				List<String> fileList = dco.getToBeDeleted();
				for (String filePath : fileList) {
					File fileToDelete = new File(repo.getWorkTree(), filePath);
					if (fileToDelete.exists())
						FileUtils.delete(fileToDelete, FileUtils.RECURSIVE
								| FileUtils.RETRY);
				}
			}
		} finally {
			dc.unlock();
		}
		RevWalk rw = new RevWalk(repo);
		RevCommit commit = rw.parseCommit(repo.resolve(Constants.HEAD));
		rw.release();
		return commit;
	}

	public RevCommit call() throws Exception {
		// lock the index
		DirCache index = repo.lockDirCache();
		// generic object inserter for use here
		ObjectInserter obi = repo.newObjectInserter();

		// determine the current HEAD and the commit it is referring to
		// this will act as the first parent of our stash object
		ObjectId headCommitId = repo.resolve(Constants.HEAD + "^{commit}");
		// create a new commit object which will act as the second parent of the
		// stash object
		// second commit object:
		// tree: tree stored inside headCommitId
		// parent: headCommitId
		CommitBuilder secondParent = new CommitBuilder();
		secondParent.setCommitter(committer);
		secondParent.setAuthor(author);
		secondParent.setMessage("WIP on master: 1e41dc first commit");
		secondParent.setParentId(headCommitId);
		secondParent.setTreeId(index.writeTree(repo.newObjectInserter()));
		// save this commit
		ObjectId secondParentCommitId = obi.insert(secondParent);
		obi.flush();

		// list of the two parents
		List<AnyObjectId> stashParentSet = new ArrayList<AnyObjectId>();
		stashParentSet.add(headCommitId);
		stashParentSet.add(secondParentCommitId);

		// now that we have the two parents, create stash object which is
		// another commit
		CommitBuilder stashObject = new CommitBuilder();
		stashObject.setCommitter(committer);
		stashObject.setAuthor(author);
		stashObject.setMessage("WIP on master: 1e41dc first commit");
		stashObject.setParentIds(stashParentSet);
		stashObject.setTreeId(index.writeTree(repo.newObjectInserter()));
		// save stash object
		ObjectId stashHead = obi.insert(stashObject);
		obi.flush();

		// stash object created
		// up next, create a reference for it
		RefUpdate ru = repo.updateRef(Constants.R_STASH);
		ru.setNewObjectId(stashHead);
		// this is going to be ObjectId.zeroId() if it's the
		// first
		// stash
		// if not, grab the id from what the ref currently
		// points to
		ru.setExpectedOldObjectId(ObjectId.zeroId());
		ru.forceUpdate();

		// up next create a merge of the current head with the current workspace
		// ie checkout from commit at head
		checkoutCurrentHead(index);
		// RevWalk revWalk = new RevWalk(repo);
		/*
		 * try { // get commit at head RevCommit commitAtHead =
		 * revWalk.parseCommit(headCommitId); // merge this with workspace
		 * repo.writeMergeHeads(Arrays.asList(commitAtHead, headCommitId));
		 * mergeStrategy.newMerger(repo); } catch (Exception e) {
		 * e.printStackTrace(); }
		 */
		return null;
	}

	private static HashMap<String, String> cloneEnv() {
		return new HashMap<String, String>(System.getenv());
	}
}
