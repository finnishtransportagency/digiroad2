(function(root) {
    root.AssetsVerificationCollection = function(backend) {

        this.fetch = function(center, typeId) {
            console.log(center)
            backend.getMunicipalityFromCoordinates(center[0], center[1]).then(function (vkmResult) {
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