(function (root) {
    root.MunicipalitySituationCollection = function (backend) {

        this.fetchDashBoardInfo = function (municipalityId) {
            backend.getDashBoardInfoByMunicipality(municipalityId).then(
                function (results) {
                    eventbus.trigger('dashBoardInfoAssets:fetched', results);
                });
        };
    };
})(this);
