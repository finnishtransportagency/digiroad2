(function (root) {
    root.MunicipalitySituationCollection = function (backend) {

        this.fetchDashBoardInfo = function () {
            backend.getDashBoardInfoByMunicipality().then(
                function (results) {
                    eventbus.trigger('dashBoardInfoAssets:fetched', results);
                });
        };
    };
})(this);
