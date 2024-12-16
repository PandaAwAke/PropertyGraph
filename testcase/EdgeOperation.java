class EdgeOperation {
    public EdgeOperation(String sourceJobName, String targetJobName) {
        this.source = Jenkins.getInstance().getItemByFullName(sourceJobName.trim(), AbstractProject.class);
        this.target = Jenkins.getInstance().getItemByFullName(targetJobName, AbstractProject.class);
        source.checkPermission(Permission.CONFIGURE);
        target.checkPermission(Permission.CONFIGURE);
    }
}