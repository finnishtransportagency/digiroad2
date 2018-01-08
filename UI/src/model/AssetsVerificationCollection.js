(function(root) {
    root.AssetsVerificationCollection = function(backend) {

        this.fetch = function(boundingBox, typeId) {
            return backend.getVerificationInfo(boundingBox, typeId).then(function(result) {
                var verified = result.verified ? true : false;
                eventbus.trigger('verificationInfo:fetched', verified);
            });
        };
    };
})(this);