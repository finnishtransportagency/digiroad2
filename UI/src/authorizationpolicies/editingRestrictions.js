(function(root) {
    root.EditingRestrictions = function() {
        var me = this;
        var backend = new Backend();
        me.restrictions = [];
        me.fetched = false;

        backend.getEditingRestrictions();

        eventbus.on('editingRestrictions:fetched', function(restrictions) {
            me.restrictions = restrictions;
            me.fetched = true;
        });

        me.hasStateRestriction = function(linearAsset, typeId) {
            return _.some(linearAsset, function(asset){
                var restriction = _.find(me.restrictions, { municipalityId: asset.municipalityCode });
                if (asset.administrativeClass) {
                    if (asset.administrativeClass === 1 || asset.administrativeClass === 'State') {
                        return restriction && restriction.stateRoadRestrictedAssetTypes.includes(typeId);
                    }
                // for lanes, admin class has to be derived from links
                } else {
                    var adminClasses = _.map(asset.selectedLinks, 'administrativeClass');
                    if (adminClasses.includes(1)) {
                        return restriction && restriction.stateRoadRestrictedAssetTypes.includes(typeId);
                    }
                }
                return false;
            });
        };

        me.hasMunicipalityRestriction = function(linearAsset, typeId) {
            return _.some(linearAsset, function(asset){
                var restriction = _.find(me.restrictions, {municipalityId: asset.municipalityCode});
                if (asset.administrativeClass) {
                    if (asset.administrativeClass === 2 || asset.administrativeClass === 'Municipality') {
                        return restriction && restriction.municipalityRoadRestrictedAssetTypes.includes(typeId);
                    }
                } else {
                    var adminClasses = _.map(asset.selectedLinks, 'administrativeClass');
                    if (adminClasses.includes(2)) {
                        return restriction && restriction.municipalityRoadRestrictedAssetTypes.includes(typeId);
                    }
                }
                return false;
            });
        };

        me.hasRestrictions = function (linearAsset, typeId) {
            return me.hasStateRestriction(linearAsset, typeId) || me.hasMunicipalityRestriction(linearAsset, typeId);
        };

        me.pointAssetHasRestriction = function (municipalityCode, adminClass, typeId) {
            var restriction = _.find(me.restrictions, { municipalityId: municipalityCode});
            if (adminClass === '1' || adminClass === 'State') {
                return restriction && restriction.stateRoadRestrictedAssetTypes.includes(typeId);
            }
            if (adminClass === '2' || adminClass === 'Municipality') {
                return restriction && restriction.municipalityRoadRestrictedAssetTypes.includes(typeId);
            }
            return false;
        };

        me.elyUserRestrictionOnMunicipalityAsset = function (authorizationPolicy, linearAsset) {
            if (!authorizationPolicy.isElyMaintainer()) return false;
            return _.some(linearAsset, function (asset) {
                return asset.administrativeClass === 2 || asset.administrativeClass === 'Municipality';
            });
        };

    };
})(this);


