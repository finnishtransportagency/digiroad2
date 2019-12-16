(function(root) {
    root.AssetsVerificationCollection = function(backend) {

        this.fetch = function (boundingBox, center, typeId, hasMunicipalityValidation) {
            if(!hasMunicipalityValidation)
                eventbus.trigger('verificationInfo:fetched', false);
            else {
                backend.getMunicipalityFromCoordinates(center[0], center[1], function (vkmResult) {
                    if (!_.isEmpty(vkmResult) && vkmResult.kuntakoodi)
                        getVerificationInfo(vkmResult.kuntakoodi, typeId);
                    else
                        setMunicipalityInfo(boundingBox, typeId);
                }, function () {
                    setMunicipalityInfo(boundingBox, typeId);
                });
            }
        };

        function getVerificationInfo(municipalityInfo, typeId) {
            backend.getVerificationInfo(municipalityInfo, typeId).then(
                function (result) {
                    var verified = result ? result.verified : false;
                    eventbus.trigger('verificationInfo:fetched', verified);
                },
                function () {
                    eventbus.trigger('verificationInfo:fetched', false);
                }
            );
        }

        function setMunicipalityInfo(boundingBox, typeId) {
            backend.getMunicipalityByBoundingBox(boundingBox).then(function (municipalityInfo) {
                if (municipalityInfo) {
                    getVerificationInfo(municipalityInfo, typeId);
                } else {
                    eventbus.trigger('verificationInfo:fetched', false);
                }
            });
        }
    };

})(this);