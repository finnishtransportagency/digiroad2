(function(root) {
    root.CyclingAndWalkingAuthorizationPolicy = function() {
        AuthorizationPolicy.call(this);

        var me = this;

        this.formEditModeAccess = function(selectedAsset) {
            var isValidMaintainer = (((me.isMunicipalityMaintainer() || me.isElyMaintainer()) && me.hasRightsInMunicipality(selectedAsset.municipalityCode)));
            var assetNotInConstructionType = ["1","3"].indexOf(selectedAsset.constructionType.toString()) < 0;

            return (!me.isState(selectedAsset) && isValidMaintainer && assetNotInConstructionType) || me.isOperator() ;
        };

    };
})(this);