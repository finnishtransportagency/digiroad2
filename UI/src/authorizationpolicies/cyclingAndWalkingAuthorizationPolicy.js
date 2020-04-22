(function(root) {
    root.CyclingAndWalkingAuthorizationPolicy = function() {
        AuthorizationPolicy.call(this);

        var me = this;

        this.formEditModeAccess = function(selectedAsset) {
            var isValidOperator = (((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.municipalityCode)) || me.isOperator());
            var assetNotInConstructionType = ["1","3"].indexOf(selectedAsset.constructionType.toString()) < 0;

            return !me.isState(selectedAsset) && isValidOperator && assetNotInConstructionType ;
        };

    };
})(this);