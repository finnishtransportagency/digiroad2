(function(root) {
    root.AssetsVerificationCollection = function(backend) {

        this.fetch = function (boundingBox, center, typeId, hasMunicipalityValidation) {
            if(!hasMunicipalityValidation)
                eventbus.trigger('verificationInfo:fetched', false);
            else {
                backend.getMunicipalityFromCoordinates(center[0], center[1], function (vkmResult) {
                    if (vkmResult.kuntakoodi)
                        backend.getVerificationInfo(vkmResult.kuntakoodi, typeId).then(function (result) {
                            var verified = result ? result.verified : false;
                            eventbus.trigger('verificationInfo:fetched', verified);
                        });
                    else {
                        backend.getMunicipalityByBoundingBox(boundingBox).then(function (municipalityInfo) {
                            if (municipalityInfo) {
                                backend.getVerificationInfo(municipalityInfo, typeId).then(function (result) {
                                    var verified = result ? result.verified : false;
                                    eventbus.trigger('verificationInfo:fetched', verified);
                                });
                            } else {
                                eventbus.trigger('verificationInfo:fetched', false);
                            }
                        });
                    }
                });
            }
        };
    };

})(this);