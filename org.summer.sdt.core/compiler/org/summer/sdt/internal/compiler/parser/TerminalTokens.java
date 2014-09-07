/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.summer.sdt.internal.compiler.parser;

/**
 * IMPORTANT NOTE: These constants are dedicated to the internal Scanner implementation.
 * It is mirrored in org.eclipse.jdt.core.compiler public package where it is API.
 * The mirror implementation is using the backward compatible ITerminalSymbols constant
 * definitions (stable with 2.0), whereas the internal implementation uses TerminalTokens
 * which constant values reflect the latest parser generation state.
 */
/**
 * Maps each terminal symbol in the java-grammar into a unique integer.
 * This integer is used to represent the terminal when computing a parsing action.
 *
 * Disclaimer : These constant values are generated automatically using a Java
 * grammar, therefore their actual values are subject to change if new keywords
 * were added to the language (for instance, 'assert' is a keyword in 1.4).
 */
public interface TerminalTokens {

	// special tokens not part of grammar - not autogenerated
	int TokenNameNotAToken = 0,
		TokenNameWHITESPACE = 1000,
		TokenNameCOMMENT_LINE = 1001,
		TokenNameCOMMENT_BLOCK = 1002,
		TokenNameCOMMENT_JAVADOC = 1003;

//	int TokenNameIdentifier = 22,
//		TokenNameabstract = 51,
//		TokenNameassert = 72,
//		TokenNameboolean = 97,
//		TokenNamebreak = 73,
//		TokenNamebyte = 98,
//		TokenNamecase = 99,
//		TokenNamecatch = 100,
//		TokenNamechar = 101,
//		TokenNameclass = 67,
//		TokenNamecontinue = 74,
//		TokenNameconst = 116,
//		TokenNamedefault = 75,
//		TokenNamedo = 76,
//		TokenNamedouble = 102,
//		TokenNameelse = 111,
//		TokenNameenum = 69,
//		TokenNameextends = 96,
//		TokenNamefalse = 38,
//		TokenNamefinal = 52,
//		TokenNamefinally = 109,
//		TokenNamefloat = 103,
//		TokenNamefor = 77,
//		TokenNamegoto = 117,
//		TokenNameif = 78,
//		TokenNameimplements = 114,
//		TokenNameimport = 104,
//		TokenNameinstanceof = 17,
//		TokenNameint = 105,
//		TokenNameinterface = 68,
//		TokenNamelong = 106,
//		TokenNamenative = 53,
//		TokenNamenew = 36,
//		TokenNamenull = 39,
//		TokenNamepackage = 95,
//		TokenNameprivate = 54,
//		TokenNameprotected = 55,
//		TokenNamepublic = 56,
//		TokenNamereturn = 79,
//		TokenNameshort = 107,
//		TokenNamestatic = 40,
//		TokenNamestrictfp = 57,
//		TokenNamesuper = 34,
//		TokenNameswitch = 80,
//		TokenNamesynchronized = 41,
//		TokenNamethis = 35,
//		TokenNamethrow = 81,
//		TokenNamethrows = 112,
//		TokenNametransient = 58,
//		TokenNametrue = 42,
//		TokenNametry = 82,
//		TokenNamevoid = 108,
//		TokenNamevolatile = 59,
//		TokenNamewhile = 71,
//		TokenNameIntegerLiteral = 43,
//		TokenNameLongLiteral = 44,
//		TokenNameFloatingPointLiteral = 45,
//		TokenNameDoubleLiteral = 46,
//		TokenNameCharacterLiteral = 47,
//		TokenNameStringLiteral = 48,
//		TokenNamePLUS_PLUS = 1,
//		TokenNameMINUS_MINUS = 2,
//		TokenNameEQUAL_EQUAL = 19,
//		TokenNameLESS_EQUAL = 12,
//		TokenNameGREATER_EQUAL = 13,
//		TokenNameNOT_EQUAL = 20,
//		TokenNameLEFT_SHIFT = 18,
//		TokenNameRIGHT_SHIFT = 14,
//		TokenNameUNSIGNED_RIGHT_SHIFT = 16,
//		TokenNamePLUS_EQUAL = 84,
//		TokenNameMINUS_EQUAL = 85,
//		TokenNameMULTIPLY_EQUAL = 86,
//		TokenNameDIVIDE_EQUAL = 87,
//		TokenNameAND_EQUAL = 88,
//		TokenNameOR_EQUAL = 89,
//		TokenNameXOR_EQUAL = 90,
//		TokenNameREMAINDER_EQUAL = 91,
//		TokenNameLEFT_SHIFT_EQUAL = 92,
//		TokenNameRIGHT_SHIFT_EQUAL = 93,
//		TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL = 94,
//		TokenNameOR_OR = 31,
//		TokenNameAND_AND = 30,
//		TokenNamePLUS = 4,
//		TokenNameMINUS = 5,
//		TokenNameNOT = 62,
//		TokenNameREMAINDER = 7,
//		TokenNameXOR = 23,
//		TokenNameAND = 21,
//		TokenNameMULTIPLY = 6,
//		TokenNameOR = 25,
//		TokenNameTWIDDLE = 63,
//		TokenNameDIVIDE = 8,
//		TokenNameGREATER = 15,
//		TokenNameLESS = 11,
//		TokenNameLPAREN = 24,
//		TokenNameRPAREN = 26,
//		TokenNameLBRACE = 49,
//		TokenNameRBRACE = 32,
//		TokenNameLBRACKET = 10,
//		TokenNameRBRACKET = 64,
//		TokenNameSEMICOLON = 28,
//		TokenNameQUESTION = 29,
//		TokenNameCOLON = 61,
//		TokenNameCOMMA = 33,
//		TokenNameDOT = 3,
//		TokenNameEQUAL = 70,
//		TokenNameAT = 37,
//		TokenNameELLIPSIS = 113,
//		TokenNameARROW = 110,
//		TokenNameCOLON_COLON = 9,
//		TokenNameBeginLambda = 50,
//		TokenNameBeginIntersectionCast = 65,
//		TokenNameBeginTypeArguments = 83,
//		TokenNameElidedSemicolonAndRightBrace = 66,
//		TokenNameAT308 = 27,
//		TokenNameAT308DOTDOTDOT = 115,
//		TokenNameEOF = 60,
//		TokenNameERROR = 118;
	
	int
	TokenNameIdentifier = 22,
    TokenNameabstract = 61,
    TokenNameassert = 79,
    TokenNameboolean = 32,
    TokenNamebreak = 80,
    TokenNamebyte = 33,
    TokenNamecase = 108,
    TokenNamecatch = 106,
    TokenNamechar = 34,
    TokenNameclass = 77,
    TokenNamecontinue = 81,
    TokenNameconst = 121,
    TokenNamedefault = 82,
    TokenNamedo = 83,
    TokenNamedouble = 35,
    TokenNameelse = 112,
    TokenNameenum = 92,
    TokenNameextends = 105,
    TokenNamefalse = 47,
    TokenNamefinal = 62,
    TokenNamefinally = 109,
    TokenNamefloat = 36,
    TokenNamefor = 84,
    TokenNamegoto = 122,
    TokenNameif = 85,
    TokenNameimplements = 118,
    TokenNameimport = 107,
    TokenNameinstanceof = 16,
    TokenNameint = 37,
    TokenNameinterface = 86,
    TokenNamelong = 38,
    TokenNamenative = 63,
    TokenNamenew = 45,
    TokenNamenull = 48,
    TokenNamepackage = 93,
    TokenNameprivate = 64,
    TokenNameprotected = 65,
    TokenNamepublic = 66,
    TokenNamereturn = 87,
    TokenNameshort = 39,
    TokenNamestatic = 58,
    TokenNamestrictfp = 67,
    TokenNamesuper = 44,
    TokenNameswitch = 88,
    TokenNamesynchronized = 59,
    TokenNamethis = 43,
    TokenNamethrow = 89,
    TokenNamethrows = 113,
    TokenNametransient = 68,
    TokenNametrue = 49,
    TokenNametry = 90,
    TokenNamevoid = 40,
    TokenNamevolatile = 69,
    TokenNamewhile = 78,
    TokenNameref = 123,
    TokenNameout = 124,
    TokenNameget = 114,
    TokenNameset = 115,
    TokenNameadd = 116,
    TokenNameremove = 117,
    TokenNameevent = 119,
    TokenNamemodule = 110,
    TokenNameexport = 125,
    TokenNamefunction = 42,
    TokenNameIntegerLiteral = 50,
    TokenNameLongLiteral = 51,
    TokenNameFloatingPointLiteral = 52,
    TokenNameDoubleLiteral = 53,
    TokenNameCharacterLiteral = 54,
    TokenNameStringLiteral = 55,
    TokenNamePLUS_PLUS = 2,
    TokenNameMINUS_MINUS = 3,
    TokenNameEQUAL_EQUAL = 19,
    TokenNameLESS_EQUAL = 12,
    TokenNameGREATER_EQUAL = 13,
    TokenNameNOT_EQUAL = 20,
    TokenNameLEFT_SHIFT = 18,
    TokenNameRIGHT_SHIFT = 15,
    TokenNameUNSIGNED_RIGHT_SHIFT = 17,
    TokenNamePLUS_EQUAL = 94,
    TokenNameMINUS_EQUAL = 95,
    TokenNameMULTIPLY_EQUAL = 96,
    TokenNameDIVIDE_EQUAL = 97,
    TokenNameAND_EQUAL = 98,
    TokenNameOR_EQUAL = 99,
    TokenNameXOR_EQUAL = 100,
    TokenNameREMAINDER_EQUAL = 101,
    TokenNameLEFT_SHIFT_EQUAL = 102,
    TokenNameRIGHT_SHIFT_EQUAL = 103,
    TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL = 104,
    TokenNameOR_OR = 29,
    TokenNameAND_AND = 28,
    TokenNamePLUS = 4,
    TokenNameMINUS = 5,
    TokenNameNOT = 70,
    TokenNameREMAINDER = 9,
    TokenNameXOR = 23,
    TokenNameAND = 21,
    TokenNameMULTIPLY = 8,
    TokenNameOR = 25,
    TokenNameTWIDDLE = 71,
    TokenNameDIVIDE = 10,
    TokenNameGREATER = 14,
    TokenNameLESS = 11,
    TokenNameLPAREN = 24,
    TokenNameRPAREN = 27,
    TokenNameLBRACE = 46,
    TokenNameRBRACE = 31,
    TokenNameLBRACKET = 6,
    TokenNameRBRACKET = 72,
    TokenNameSEMICOLON = 30,
    TokenNameQUESTION = 26,
    TokenNameCOLON = 60,
    TokenNameCOMMA = 41,
    TokenNameDOT = 1,
    TokenNameEQUAL = 76,
    TokenNameAT = 57,
    TokenNameELLIPSIS = 120,
    TokenNameARROW = 111,
    TokenNameCOLON_COLON = 7,
    TokenNameBeginLambda = 56,
    TokenNameBeginIntersectionCast = 73,
    TokenNameBeginTypeArguments = 91,
    TokenNameElidedSemicolonAndRightBrace = 75,
    TokenNameCLOSE_TAG = 126,
    TokenNameCLOSE_ELEMENT = 127,
    TokenNameEOF = 74,
	TokenNameERROR = 128;
}
