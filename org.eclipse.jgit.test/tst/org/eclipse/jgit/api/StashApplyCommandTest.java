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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class StashApplyCommandTest extends RepositoryTestCase {
	private Git git;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

		// create test files
		writeTrashFile("File1.txt", "original unstashed content\n");

		// commit changes
		git.add().addFilepattern("File1.txt").call();
		git.commit().setMessage("Test file commit").call();
	}

	@Test
	public void testStash() {
		try {
			// edit and output file
			editFile(git.getRepository().getWorkTree() + "/File1.txt",
					"stashed content");

			// 1) call stash create command
			git.stashCreate().call();
			// 2) call stash list
			git.stashList().call();
			// 2.5) edit file again and create commit
			editFile(git.getRepository().getWorkTree() + "/File1.txt",
					"edit 2");
			// 2.6) add and commit change
			git.add().addFilepattern("File1.txt").call();
			git.commit().setMessage("second commit").call();
			// 3) apply stash
			git.stashApply().call();
			// 4) verify stash applied by outputting file
			readFile(git.getRepository().getWorkTree() + "/File1.txt");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void readFile(String fileName) {
		try {
			FileReader input = new FileReader(fileName);
			BufferedReader bufRead = new BufferedReader(input);

			String line;
			int count = 0;

			line = bufRead.readLine();
			count++;

			while (line != null) {
				System.out.println("File Contents: " + line);
				line = bufRead.readLine();
				count++;
			}

			bufRead.close();
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println(e.getStackTrace());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void editFile(String fileName, String mssg) {
		try {
			FileWriter output = new FileWriter(fileName);
			BufferedWriter bufWrite = new BufferedWriter(output);

			output.write(mssg);

			bufWrite.close();

		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Usage: java ReadFile filename\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
