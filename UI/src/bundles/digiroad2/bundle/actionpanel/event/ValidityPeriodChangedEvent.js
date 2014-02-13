Oskari.clazz.define('Oskari.digiroad2.bundle.actionpanel.event.ValidityPeriodChangedEvent',
  function(selectedValidityPeriods) {
    this._selectedValidityPeriods = selectedValidityPeriods;
  }, {
    __name: 'actionpanel.ValidityPeriodChangedEvent',
    
    getName: function() {
      return this.__name;
    },

    getSelectedValidityPeriods: function() {
      return this._selectedValidityPeriods;
    }
  },
  {'protocol' : ['Oskari.mapframework.event.Event']}
);
