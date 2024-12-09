class FilenameGuardFileterNew {
    private String guard(String filename) {
        String guarded = filename.replace(":", "_");
        guarded = FileSystems.getDefault().getPath(guarded).normalize().toString();
        if (LOG.isDebugEnabled()) {
            LOG.debug("guarded " + filename + " to " + guarded);
        }
        return guarded;
    }
}