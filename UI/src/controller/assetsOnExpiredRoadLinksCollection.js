(function(root) {
    root.AssetsOnExpiredRoadLinksCollection = function(backend) {
        var me = this;

        this.fetch = function(assetId, callback) {
            backend.getAssetsOnExpiredLinks(assetId, callback);
        };

    };
})(this);
