(function(root) {
    root.PopUpCollection = function(backend) {
        var me = this;

        this.fetch = function(assetId, callback) {
            backend.getPointAssetsOnExpiredLinks(assetId, callback);
        };

    };
})(this);
