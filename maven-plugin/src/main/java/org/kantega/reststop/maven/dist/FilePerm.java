package org.kantega.reststop.maven.dist;

/**

 */
public class FilePerm {

    public static final FilePerm DEFAULT = new FilePerm("0644","0755","%{name}","%{name}");
    private String fileMode ;
    private String dirMode;
    private String user;
    private String group;

    public FilePerm(String fileMode, String dirMode, String user, String group) {
        this.fileMode = fileMode;
        this.dirMode = dirMode;
        this.user = user;
        this.group = group;
    }

    public FilePerm() {
    }

    public String getFileMode() {
        return fileMode;
    }

    public void setFileMode(String fileMode) {
        this.fileMode = standardizeMode(fileMode);
    }

    public String getDirMode() {
        return dirMode;
    }

    public void setDirMode(String dirMode) {
        this.dirMode = standardizeMode(dirMode);
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    private static String standardizeMode(String mode){
        if( mode == null)
            return null;

        if( mode.length() == 3)
            return "0"+mode;

        return mode;
    }
}

