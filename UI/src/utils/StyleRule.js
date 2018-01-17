(function(root) {
    root.StyleRule = function(){
        var expressionFn = [];
        var styles = [];

        var runExpression = function(expression, obj, previousCondition){
            if(previousCondition !== undefined)
                return expression.compareExpressions(previousCondition, expression.compare(expression.getValue(obj)));
            return expression.compare(expression.getValue(obj));
        };

        var generateExpression = function(property, propertyValue) {

          var expression = _.isFunction(property)?
            {getValue: property} :
            (propertyValue ?
              {getValue: function() {return propertyValue;}} :
              {getValue: function(obj) {return obj[property];}}
            );
          expressionFn.push(expression);
          return expression;
        };


        this.where = function(property, propertyValue){
            generateExpression(property, propertyValue);
            return this;
        };

        this.and = function(property, propertyValue){
            if(expressionFn.length === 0)
                throw 'You must have a "where" function before use the "and" function.';
            var expression = generateExpression(property, propertyValue);
            expression.compareExpressions = function(arg1, arg2) { return arg1 && arg2; };
            return this;
        };

        this.or = function(property, propertyValue){
            if(expressionFn.length === 0)
                throw 'You must have a "where" function before use the "or" function.';
            var expression = generateExpression(property, propertyValue);
            expression.compareExpressions = function(arg1, arg2) { return arg1 || arg2; };
            return this;
        };

        this.isDefined = function(){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "is".';
            expression.compare = function(propertyValue){
                return !_.isUndefined(propertyValue);
            };
            return this;
        };

        this.isUndefined = function(){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "is".';
            expression.compare = function(propertyValue){
                return _.isUndefined(propertyValue);
            };
            return this;
        };

        this.is = function(value){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "is".';
            expression.compare = function(propertyValue){
                return propertyValue == value;
            };
            return this;
        };

        this.isNot = function(value){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "isNot".';
            expression.compare = function(propertyValue){
                return propertyValue != value;
            };
            return this;
        };

        this.isIn = function(values){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "isIn".';
            expression.compare = function(propertyValue){
                for(var i=0; i < values.length ; ++i)
                    if(values[i] == propertyValue)
                        return true;
                return false;
            };
            return this;
        };

        this.isNotIn = function(values){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "isNotIn".';
            expression.compare = function(propertyValue){
                var exists = false;
                for(var i=0; i < values.length ; ++i)
                    if(values[i] == propertyValue)
                        exists = true;
                return !exists;
            };
            return this;
        };

      this.isBetween = function(values){
        var expression = expressionFn[expressionFn.length-1];
        if(!expression || expression.compare)
          throw 'You must have on of the following functions ["where", "and", "or"] before use the "isBetween".';
        expression.compare = function(propertyValue){
            return values[0] <= propertyValue  && propertyValue < values[1];
        };
        return this;
      };

        this.use = function(obj){
            styles.push(obj);
            return this;
        };

        this._match = function(obj){
            if(expressionFn.length === 0)
                return false;

            var condition = runExpression(expressionFn[0], obj);
            for(var i=1 ; i < expressionFn.length; i++)
                condition = runExpression(expressionFn[i], obj, condition);

            return condition;
        };

        this._get = function(){
            return styles;
        };
    };

    root.StyleRuleProvider = function(defaultStyle){

        var mergeColorOpacity = function(color, opacity){
            var rgb = {};

            if(color.substring(0, 1) != '#' || !opacity) { return color; }

            if (color.length == 7) {
                rgb.r = parseInt(color.substring(1, 3), 16);
                rgb.g = parseInt(color.substring(3, 5), 16);
                rgb.b = parseInt(color.substring(5, 7), 16);
            }
            else if (color.length == 4) {
                rgb.r = parseInt(color.substring(1, 2) + color.substring(1, 2), 16);
                rgb.g = parseInt(color.substring(2, 3) + color.substring(2, 3), 16);
                rgb.b = parseInt(color.substring(3, 4) + color.substring(3, 4), 16);
            }
            else {
                return color;
            }

            rgb.css = 'rgb' + (opacity ? 'a' : '') + '(';
            rgb.css += rgb.r + ',' + rgb.g + ',' + rgb.b;
            rgb.css += (opacity ? ',' + opacity : '') + ')';

            return rgb.css;
        };

        var stateRules = [];
        var defaultRuleName = 'default';
        var openLayerStyleClassConfigs = [
            {
                name: 'stroke',
                factory: function(settings){
                    if(settings.color)
                        settings.color = mergeColorOpacity(settings.color, settings.opacity);

                    return {
                        stroke: new ol.style.Stroke(settings)
                    };
                }
            },
            {
                name: 'fill',
                factory: function(settings){
                    if(settings.color)
                        settings.color = mergeColorOpacity(settings.color, settings.opacity);

                    return { fill: new ol.style.Fill(settings) };
                }
            },
            {
                name: 'icon',
                factory: function(settings, feature){
                    //If in the future we need to have more field like rotation we should add support
                    settings.rotation = feature.getProperties().rotation;
                    return { geometry: feature.getGeometry(), image:  new ol.style.Icon((settings)) };
                }
            },
            {
                name: 'text',
                factory: function(settings){
                    return { text: new ol.style.Text(settings) };
                }
            }
        ];

        var getOpenLayerStyleConf = function(name){
            for(var i in openLayerStyleClassConfigs)
                if(openLayerStyleClassConfigs[i].name == name)
                    return openLayerStyleClassConfigs[i];
            return undefined;
        };

        var createOpenLayerStyle = function(configObj, feature){
            var styleOptions = {};
            for(var propertyName in configObj){
                var olConf = getOpenLayerStyleConf(propertyName);
                if(olConf)
                    _.merge(styleOptions, olConf.factory(configObj[propertyName], feature));
                else
                    styleOptions[propertyName] = configObj[propertyName];
            }
            return new ol.style.Style(styleOptions);
        };

        var getRulesByName = function(name){
            var stateRule  = stateRules[name];
            if(stateRule)
                return stateRule;
            return [];
        };

        var setRulesByName = function(name, rules){
            stateRules[defaultRuleName] = rules;
        };

        this.addRules = function(rules){
            this.addRulesByName(defaultRuleName, rules);
        };

        this.addRulesByName = function(name, rules){
            var allRules = getRulesByName(name);
            for(var i=0; i < rules.length; i++)
                allRules.push(rules[i]);
            setRulesByName(name, allRules);
        };

        this.getStyle = function(feature, extraProperties){
            return this.getStyleByName(defaultRuleName, feature, extraProperties);
        };

        this.getStyleByName = function(name, feature, extraProperties){
            var context = _.merge({}, feature.getProperties(), extraProperties);
            var allRules = getRulesByName(name);
            var configObj = _.merge({}, defaultStyle);
            for(var i=0; i < allRules.length; i++){
                var rule = allRules[i];
                if(rule._match(context)){
                    var styles = rule._get();
                    for(var j=0; j < styles.length ; j++){
                        configObj = _.merge(configObj, styles[j]);
                    }
                }
            }
            return createOpenLayerStyle(configObj, feature);
        };
    };
})(this);
