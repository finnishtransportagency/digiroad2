(function (root) {
    root.MunicipalitySituationCollection = function (backend) {

        this.fetchVerificationInfoCriticalAssets = function (municipalityId) {
            backend.getCriticalAssetTypesVerificationInfoByMunicipality(municipalityId).then(
                function (results) {
                    eventbus.trigger('verificationInfoCriticalAssets:fetched', results);
                });
        };
    };
})(this);
