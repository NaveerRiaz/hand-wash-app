package com.aj.videplayertest;

import java.io.File;

public class FileUtils {

    /**
     * Deletes a folder and all of its contents.
     *
     * @param folder The folder to delete.
     * @return true if the folder and all of its contents were deleted successfully; false otherwise.
     */
    public static boolean deleteFolder(File folder) {
        if (folder.exists()) {
            // Check if the file is a directory
            if (folder.isDirectory()) {
                // Get all files and folders in the directory
                String[] children = folder.list();
                // Iterate through each file/folder and delete them
                for (int i = 0; i < children.length; i++) {
                    boolean success = deleteFolder(new File(folder, children[i]));
                    if (!success) {
                        // If unable to delete, return false
                        return false;
                    }
                }
            }
            // Delete the folder (or file) itself
            return folder.delete();
        }
        // If folder does not exist, consider it a success.
        return true;
    }
}
