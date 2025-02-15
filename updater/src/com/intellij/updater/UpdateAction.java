// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.io.*;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.updater.Runner.LOG;

public class UpdateAction extends BaseUpdateAction {
  public UpdateAction(Patch patch, String path, long checksum) {
    this(patch, path, path, checksum, false);
  }

  public UpdateAction(Patch patch, String path, String source, long checksum, boolean move) {
    super(patch, path, source, checksum, move);
  }

  public UpdateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    // if the file is optional in may not exist
    // If file is critical, we can restore it.
    if (isOptional() && !isCritical()) {
      return getSource(toDir).exists();
    }
    return true;
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    if (!isMove()) {
      patchOutput.putNextEntry(new ZipEntry(getPath()));

      FileType type = getFileType(newerFile);
      if (type == FileType.SYMLINK) throw new IOException("Unexpected symlink: " + newerFile);
      writeFileType(patchOutput, type);
      try (InputStream olderFileIn = new BufferedInputStream(Utils.newFileInputStream(olderFile));
           InputStream newerFileIn = new BufferedInputStream(new FileInputStream(newerFile))) {
        writeDiff(olderFileIn, newerFileIn, patchOutput);
      }

      patchOutput.closeEntry();
    }
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    LOG.info("Update action. File: " + toFile.getAbsolutePath());

    File source = mandatoryBackup() ? getSource(Objects.requireNonNull(backupDir)) : toFile;
    if (!isMove()) {
      try (InputStream in = Utils.findEntryInputStream(patchFile, getPath())) {
        if (in == null) {
          throw new IOException("Invalid entry " + getPath());
        }

        FileType type = readFileType(in);
        File tempFile = Utils.getTempFile(toFile.getName());
        if (isCritical()) {
          // If the file is critical, we always store the full file in the patch. So, we can just restore it from the patch.
          try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            applyDiff(in, null, out);
          }
        }
        else {
          try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
               InputStream oldFileIn = source.exists() ? Utils.newFileInputStream(source) : null) {
            applyDiff(in, oldFileIn, out);
          }
        }

        if (type == FileType.EXECUTABLE_FILE) {
          Utils.setExecutable(tempFile);
        }

        source = tempFile;
      }
    }

    replaceUpdated(source, toFile);
  }
}
