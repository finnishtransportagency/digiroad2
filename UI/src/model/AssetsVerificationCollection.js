(function(root) {
    root.AssetsVerificationCollection = function(backend) {

        this.fetch = function(center, typeId) {
            backend.getMunicipalityFromCoordinates(center[0], center[1], function (vkmResult) {
                if(vkmResult.kuntakoodi) {
                    backend.getVerificationInfo(vkmResult.kuntakoodi, typeId).then(function (result) {
                        var verified = result ? result.verified : false;
                        eventbus.trigger('verificationInfo:fetched', verified);
                    });
                } else
                    eventbus.trigger('verificationInfo:fetched', false);
            });
        };
    };
})(this);