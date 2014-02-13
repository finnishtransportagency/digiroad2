Oskari.clazz.define('Oskari.digiroad2.bundle.actionpanel.event.ValidityPeriodUncheckedEvent',
  function(validityPeriod) {
    this._validityPeriod = validityPeriod;
  }, {
    __name: 'actionpanel.ValidityPeriodUncheckedEvent',
    
    getName: function() {
      return this.__name;
    },

    getValidityPeriod: function() {
      return this._validityPeriod;
    }
  },
  {'protocol' : ['Oskari.mapframework.event.Event']}
);