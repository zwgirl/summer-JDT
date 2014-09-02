/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla - Contribution for bug 239066
 *     Stephan Herrmann  - Contributions for
 *	     						bug 236385 - [compiler] Warn for potential programming problem if an object is created but not used
 *  	   						bug 338303 - Warning about Redundant assignment conflicts with definite assignment
 *								bug 349326 - [1.7] new warning for missing try-with-resources
 *								bug 186342 - [compiler][null] Using annotations for null checking
 *								bug 365519 - editorial cleanup after bug 186342 and bug 365387
 *								bug 365662 - [compiler][null] warn on contradictory and redundant null annotations
 *								bug 365531 - [compiler][null] investigate alternative strategy for internally encoding nullness defaults
 *								bug 365859 - [compiler][null] distinguish warnings based on flow analysis vs. null annotations
 *								bug 374605 - Unreasonable warning for enum-based switch statements
 *								bug 382353 - [1.8][compiler] Implementation property modifiers should be accepted on default methods.
 *								bug 382347 - [1.8][compiler] Compiler accepts incorrect default method inheritance
 *								bug 388281 - [compiler][null] inheritance of null annotations as an option
 *								bug 376053 - [compiler][resource] Strange potential resource leak problems
 *								bug 381443 - [compiler][null] Allow parameter widening from @NonNull to unannotated
 *								bug 393719 - [compiler] inconsistent warnings on iteration variables
 *								bug 392862 - [1.8][compiler][null] Evaluate null annotations on array types
 *								bug 388739 - [1.8][compiler] consider default methods when detecting whether a class needs to be declared abstract
 *								bug 331649 - [compiler][null] consider null annotations for fields
 *								bug 382789 - [compiler][null] warn when syntactically-nonnull expression is compared against null
 *								bug 376590 - Private fields with @Inject are ignored by unused field validation
 *								bug 400761 - [compiler][null] null may be return as boolean without a diagnostic
 *								bug 402028 - [1.8][compiler] null analysis for reference expressions 
 *								bug 401796 - [1.8][compiler] don't treat default methods as overriding an independent inherited abstract method
 *								bug 404649 - [1.8][compiler] detect illegal reference to indirect or redundant super
 *								bug 392384 - [1.8][compiler][null] Restore nullness info from type annotations in class files
 *								Bug 392099 - [1.8][compiler][null] Apply null annotation on types for null analysis
 *								Bug 415043 - [1.8][null] Follow-up re null type annotations after bug 392099
 *								Bug 415291 - [1.8][null] differentiate type incompatibilities due to null annotations
 *								Bug 415850 - [1.8] Ensure RunJDTCoreTests can cope with null annotations enabled
 *								Bug 414380 - [compiler][internal] QualifiedNameReference#indexOfFirstFieldBinding does not point to the first field
 *								Bug 392238 - [1.8][compiler][null] Detect semantically invalid null type annotations
 *								Bug 416307 - [1.8][compiler][null] subclass with type parameter substitution confuses null checking
 *								Bug 400874 - [1.8][compiler] Inference infrastructure should evolve to meet JLS8 18.x (Part G of JSR335 spec)
 *								Bug 424637 - [1.8][compiler][null] AIOOB in ReferenceExpression.resolveType with a method reference to Files::walk
 *								Bug 428294 - [1.8][compiler] Type mismatch: cannot convert from List<Object> to Collection<Object[]>
 *								Bug 428366 - [1.8] [compiler] The method valueAt(ObservableList<Object>, int) is ambiguous for the type Bindings
 *								Bug 416190 - [1.8][null] detect incompatible overrides due to null type annotations
 *								Bug 392245 - [1.8][compiler][null] Define whether / how @NonNullByDefault applies to TYPE_USE locations
 *								Bug 390889 - [1.8][compiler] Evaluate options to support 1.7- projects against 1.8 JRE.
 *								Bug 430150 - [1.8][null] stricter checking against type variables
 *								Bug 434600 - Incorrect null analysis error reporting on type parameters
 *      Jesper S Moller <jesper@selskabet.org> -  Contributions for
 *								bug 382701 - [1.8][compiler] Implement semantic analysis of Lambda expressions & Reference expression
 *								bug 382721 - [1.8][compiler] Effectively final variables needs special treatment
 *								bug 384567 - [1.5][compiler] Compiler accepts illegal modifiers on package declaration
 *								bug 412153 - [1.8][compiler] Check validity of annotations which may be repeatable
 *								bug 412151 - [1.8][compiler] Check repeating annotation's collection type
 *								bug 419209 - [1.8] Repeating container annotations should be rejected in the presence of annotation it contains
 *								Bug 429384 - [1.8][null] implement conformance rules for null-annotated lower / upper type bounds
 *								Bug 416182 - [1.8][compiler][null] Contradictory null annotations not rejected
 ********************************************************************************/
package org.summer.sdt.internal.compiler.problem;

import java.io.CharConversionException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import org.summer.sdt.core.compiler.CategorizedProblem;
import org.summer.sdt.core.compiler.CharOperation;
import org.summer.sdt.core.compiler.IProblem;
import org.summer.sdt.core.compiler.InvalidInputException;
import org.summer.sdt.internal.compiler.CompilationResult;
import org.summer.sdt.internal.compiler.IErrorHandlingPolicy;
import org.summer.sdt.internal.compiler.IProblemFactory;
import org.summer.sdt.internal.compiler.ast.ASTNode;
import org.summer.sdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.summer.sdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.summer.sdt.internal.compiler.ast.AllocationExpression;
import org.summer.sdt.internal.compiler.ast.Annotation;
import org.summer.sdt.internal.compiler.ast.AnnotationMethodDeclaration;
import org.summer.sdt.internal.compiler.ast.Argument;
import org.summer.sdt.internal.compiler.ast.ArrayAllocationExpression;
import org.summer.sdt.internal.compiler.ast.ArrayInitializer;
import org.summer.sdt.internal.compiler.ast.ArrayQualifiedTypeReference;
import org.summer.sdt.internal.compiler.ast.ArrayReference;
import org.summer.sdt.internal.compiler.ast.ArrayTypeReference;
import org.summer.sdt.internal.compiler.ast.Assignment;
import org.summer.sdt.internal.compiler.ast.BinaryExpression;
import org.summer.sdt.internal.compiler.ast.Block;
import org.summer.sdt.internal.compiler.ast.BranchStatement;
import org.summer.sdt.internal.compiler.ast.CaseStatement;
import org.summer.sdt.internal.compiler.ast.CastExpression;
import org.summer.sdt.internal.compiler.ast.ClassLiteralAccess;
import org.summer.sdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.summer.sdt.internal.compiler.ast.CompoundAssignment;
import org.summer.sdt.internal.compiler.ast.ConditionalExpression;
import org.summer.sdt.internal.compiler.ast.ConstructorDeclaration;
import org.summer.sdt.internal.compiler.ast.EqualExpression;
import org.summer.sdt.internal.compiler.ast.ExplicitConstructorCall;
import org.summer.sdt.internal.compiler.ast.Expression;
import org.summer.sdt.internal.compiler.ast.FakedTrackingVariable;
import org.summer.sdt.internal.compiler.ast.FieldDeclaration;
import org.summer.sdt.internal.compiler.ast.FieldReference;
import org.summer.sdt.internal.compiler.ast.FunctionalExpression;
import org.summer.sdt.internal.compiler.ast.ImportReference;
import org.summer.sdt.internal.compiler.ast.Initializer;
import org.summer.sdt.internal.compiler.ast.InstanceOfExpression;
import org.summer.sdt.internal.compiler.ast.LabeledStatement;
import org.summer.sdt.internal.compiler.ast.LambdaExpression;
import org.summer.sdt.internal.compiler.ast.Literal;
import org.summer.sdt.internal.compiler.ast.LocalDeclaration;
import org.summer.sdt.internal.compiler.ast.MemberValuePair;
import org.summer.sdt.internal.compiler.ast.MessageSend;
import org.summer.sdt.internal.compiler.ast.MethodDeclaration;
import org.summer.sdt.internal.compiler.ast.NameReference;
import org.summer.sdt.internal.compiler.ast.NullAnnotationMatching;
import org.summer.sdt.internal.compiler.ast.NullLiteral;
import org.summer.sdt.internal.compiler.ast.ParameterizedQualifiedTypeReference;
import org.summer.sdt.internal.compiler.ast.ParameterizedSingleTypeReference;
import org.summer.sdt.internal.compiler.ast.QualifiedAllocationExpression;
import org.summer.sdt.internal.compiler.ast.QualifiedNameReference;
import org.summer.sdt.internal.compiler.ast.QualifiedTypeReference;
import org.summer.sdt.internal.compiler.ast.Receiver;
import org.summer.sdt.internal.compiler.ast.Reference;
import org.summer.sdt.internal.compiler.ast.ReferenceExpression;
import org.summer.sdt.internal.compiler.ast.ReturnStatement;
import org.summer.sdt.internal.compiler.ast.SingleNameReference;
import org.summer.sdt.internal.compiler.ast.Statement;
import org.summer.sdt.internal.compiler.ast.SwitchStatement;
import org.summer.sdt.internal.compiler.ast.ThisReference;
import org.summer.sdt.internal.compiler.ast.TypeDeclaration;
import org.summer.sdt.internal.compiler.ast.TypeParameter;
import org.summer.sdt.internal.compiler.ast.TypeReference;
import org.summer.sdt.internal.compiler.ast.UnaryExpression;
import org.summer.sdt.internal.compiler.ast.Wildcard;
import org.summer.sdt.internal.compiler.classfmt.ClassFileConstants;
import org.summer.sdt.internal.compiler.env.AccessRestriction;
import org.summer.sdt.internal.compiler.env.ICompilationUnit;
import org.summer.sdt.internal.compiler.flow.FlowInfo;
import org.summer.sdt.internal.compiler.impl.CompilerOptions;
import org.summer.sdt.internal.compiler.impl.ReferenceContext;
import org.summer.sdt.internal.compiler.lookup.ArrayBinding;
import org.summer.sdt.internal.compiler.lookup.Binding;
import org.summer.sdt.internal.compiler.lookup.CaptureBinding;
import org.summer.sdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.summer.sdt.internal.compiler.lookup.FieldBinding;
import org.summer.sdt.internal.compiler.lookup.InferenceContext18;
import org.summer.sdt.internal.compiler.lookup.InvocationSite;
import org.summer.sdt.internal.compiler.lookup.LocalVariableBinding;
import org.summer.sdt.internal.compiler.lookup.LookupEnvironment;
import org.summer.sdt.internal.compiler.lookup.MethodBinding;
import org.summer.sdt.internal.compiler.lookup.MethodScope;
import org.summer.sdt.internal.compiler.lookup.PackageBinding;
import org.summer.sdt.internal.compiler.lookup.ParameterizedGenericMethodBinding;
import org.summer.sdt.internal.compiler.lookup.ProblemMethodBinding;
import org.summer.sdt.internal.compiler.lookup.ProblemReasons;
import org.summer.sdt.internal.compiler.lookup.ProblemReferenceBinding;
import org.summer.sdt.internal.compiler.lookup.ReferenceBinding;
import org.summer.sdt.internal.compiler.lookup.Scope;
import org.summer.sdt.internal.compiler.lookup.SourceTypeBinding;
import org.summer.sdt.internal.compiler.lookup.SyntheticArgumentBinding;
import org.summer.sdt.internal.compiler.lookup.TagBits;
import org.summer.sdt.internal.compiler.lookup.TypeBinding;
import org.summer.sdt.internal.compiler.lookup.TypeConstants;
import org.summer.sdt.internal.compiler.lookup.TypeIds;
import org.summer.sdt.internal.compiler.lookup.TypeVariableBinding;
import org.summer.sdt.internal.compiler.lookup.VariableBinding;
import org.summer.sdt.internal.compiler.lookup.WildcardBinding;
import org.summer.sdt.internal.compiler.parser.JavadocTagConstants;
import org.summer.sdt.internal.compiler.parser.Parser;
import org.summer.sdt.internal.compiler.parser.RecoveryScanner;
import org.summer.sdt.internal.compiler.parser.Scanner;
import org.summer.sdt.internal.compiler.parser.TerminalTokens;
import org.summer.sdt.internal.compiler.util.Messages;
import org.summer.sdt.internal.compiler.util.Util;

@SuppressWarnings("rawtypes")
public class ProblemReporter extends ProblemHandler {

	public ReferenceContext referenceContext;
	private Scanner positionScanner;
	private boolean underScoreIsLambdaParameter;
	private final static byte
	  // TYPE_ACCESS = 0x0,
	  FIELD_ACCESS = 0x4,
	  CONSTRUCTOR_ACCESS = 0x8,
	  METHOD_ACCESS = 0xC;

	public ProblemReporter(IErrorHandlingPolicy policy, CompilerOptions options, IProblemFactory problemFactory) {
		super(policy, options, problemFactory);
	}
	
	private static int getElaborationId (int leadProblemId, byte elaborationVariant) {
		return leadProblemId << 8 | elaborationVariant; // leadProblemId comes into the higher order bytes
	}
	public static int getIrritant(int problemID) {
		switch(problemID){
	
			case IProblem.MaskedCatch :
				return CompilerOptions.MaskedCatchBlock;
	
			case IProblem.UnusedImport :
				return CompilerOptions.UnusedImport;
	
			case IProblem.MethodButWithConstructorName :
				return CompilerOptions.MethodWithConstructorName;
	
			case IProblem.OverridingNonVisibleMethod :
				return CompilerOptions.OverriddenPackageDefaultMethod;
	
			case IProblem.IncompatibleReturnTypeForNonInheritedInterfaceMethod :
			case IProblem.IncompatibleExceptionInThrowsClauseForNonInheritedInterfaceMethod :
				return CompilerOptions.IncompatibleNonInheritedInterfaceMethod;
	
			case IProblem.OverridingDeprecatedMethod :
			case IProblem.UsingDeprecatedType :
			case IProblem.UsingDeprecatedMethod :
			case IProblem.UsingDeprecatedConstructor :
			case IProblem.UsingDeprecatedField :
				return CompilerOptions.UsingDeprecatedAPI; 
	 
			case IProblem.LocalVariableIsNeverUsed :
				return CompilerOptions.UnusedLocalVariable;
	
			case IProblem.ArgumentIsNeverUsed :
				return CompilerOptions.UnusedArgument;
	
			case IProblem.NoImplicitStringConversionForCharArrayExpression :
				return CompilerOptions.NoImplicitStringConversion;
	
			case IProblem.NeedToEmulateFieldReadAccess :
			case IProblem.NeedToEmulateFieldWriteAccess :
			case IProblem.NeedToEmulateMethodAccess :
			case IProblem.NeedToEmulateConstructorAccess :
				return CompilerOptions.AccessEmulation;
	
			case IProblem.NonExternalizedStringLiteral :
			case IProblem.UnnecessaryNLSTag :
				return CompilerOptions.NonExternalizedString;
	
			case IProblem.UseAssertAsAnIdentifier :
				return CompilerOptions.AssertUsedAsAnIdentifier;
	
			case IProblem.UseEnumAsAnIdentifier :
				return CompilerOptions.EnumUsedAsAnIdentifier;
	
			case IProblem.NonStaticAccessToStaticMethod :
			case IProblem.NonStaticAccessToStaticField :
				return CompilerOptions.NonStaticAccessToStatic;
	
			case IProblem.IndirectAccessToStaticMethod :
			case IProblem.IndirectAccessToStaticField :
			case IProblem.IndirectAccessToStaticType :
				return CompilerOptions.IndirectStaticAccess;
	
			case IProblem.AssignmentHasNoEffect:
				return CompilerOptions.NoEffectAssignment;
	
			case IProblem.UnusedPrivateConstructor:
			case IProblem.UnusedPrivateMethod:
			case IProblem.UnusedPrivateField:
			case IProblem.UnusedPrivateType:
				return CompilerOptions.UnusedPrivateMember;
	
			case IProblem.LocalVariableHidingLocalVariable:
			case IProblem.LocalVariableHidingField:
			case IProblem.ArgumentHidingLocalVariable:
			case IProblem.ArgumentHidingField:
				return CompilerOptions.LocalVariableHiding;
	
			case IProblem.FieldHidingLocalVariable:
			case IProblem.FieldHidingField:
				return CompilerOptions.FieldHiding;
	
			case IProblem.TypeParameterHidingType:
			case IProblem.TypeHidingTypeParameterFromType:
			case IProblem.TypeHidingTypeParameterFromMethod:
			case IProblem.TypeHidingType:
				return CompilerOptions.TypeHiding;
	
			case IProblem.PossibleAccidentalBooleanAssignment:
				return CompilerOptions.AccidentalBooleanAssign;
	
			case IProblem.SuperfluousSemicolon:
			case IProblem.EmptyControlFlowStatement:
				return CompilerOptions.EmptyStatement;
	
			case IProblem.UndocumentedEmptyBlock:
				return CompilerOptions.UndocumentedEmptyBlock;
	
			case IProblem.UnnecessaryCast:
			case IProblem.UnnecessaryInstanceof:
				return CompilerOptions.UnnecessaryTypeCheck;
	
			case IProblem.FinallyMustCompleteNormally:
				return CompilerOptions.FinallyBlockNotCompleting;
	
			case IProblem.UnusedMethodDeclaredThrownException:
			case IProblem.UnusedConstructorDeclaredThrownException:
				return CompilerOptions.UnusedDeclaredThrownException;
	
			case IProblem.UnqualifiedFieldAccess:
				return CompilerOptions.UnqualifiedFieldAccess;
	
			case IProblem.UnnecessaryElse:
				return CompilerOptions.UnnecessaryElse;
	
			case IProblem.UnsafeRawConstructorInvocation:
			case IProblem.UnsafeRawMethodInvocation:
			case IProblem.UnsafeTypeConversion:
			case IProblem.UnsafeElementTypeConversion:
			case IProblem.UnsafeRawFieldAssignment:
			case IProblem.UnsafeGenericCast:
			case IProblem.UnsafeReturnTypeOverride:
			case IProblem.UnsafeRawGenericMethodInvocation:
			case IProblem.UnsafeRawGenericConstructorInvocation:
			case IProblem.UnsafeGenericArrayForVarargs:
			case IProblem.PotentialHeapPollutionFromVararg:
				return CompilerOptions.UncheckedTypeOperation;
	
			case IProblem.RawTypeReference:
				return CompilerOptions.RawTypeReference;
	
			case IProblem.MissingOverrideAnnotation:
			case IProblem.MissingOverrideAnnotationForInterfaceMethodImplementation:
				return CompilerOptions.MissingOverrideAnnotation;
	
			case IProblem.FieldMissingDeprecatedAnnotation:
			case IProblem.MethodMissingDeprecatedAnnotation:
			case IProblem.TypeMissingDeprecatedAnnotation:
				return CompilerOptions.MissingDeprecatedAnnotation;
	
			case IProblem.FinalBoundForTypeVariable:
			    return CompilerOptions.FinalParameterBound;
	
			case IProblem.MissingSerialVersion:
				return CompilerOptions.MissingSerialVersion;
	
			case IProblem.ForbiddenReference:
				return CompilerOptions.ForbiddenReference;
	
			case IProblem.DiscouragedReference:
				return CompilerOptions.DiscouragedReference;
	
			case IProblem.MethodVarargsArgumentNeedCast :
			case IProblem.ConstructorVarargsArgumentNeedCast :
				return CompilerOptions.VarargsArgumentNeedCast;
	
			case IProblem.NullLocalVariableReference:
			case IProblem.NullableFieldReference:
			case IProblem.NullExpressionReference:
			case IProblem.NullUnboxing:
				return CompilerOptions.NullReference;
	
			case IProblem.PotentialNullLocalVariableReference:
			case IProblem.PotentialNullMessageSendReference:
			case IProblem.ArrayReferencePotentialNullReference:
			case IProblem.DereferencingNullableExpression:
			case IProblem.PotentialNullExpressionReference:
			case IProblem.PotentialNullUnboxing:
				return CompilerOptions.PotentialNullReference;
	
			case IProblem.RedundantLocalVariableNullAssignment:
			case IProblem.RedundantNullCheckOnNonNullLocalVariable:
			case IProblem.RedundantNullCheckOnNullLocalVariable:
			case IProblem.NonNullLocalVariableComparisonYieldsFalse:
			case IProblem.NullLocalVariableComparisonYieldsFalse:
			case IProblem.NullLocalVariableInstanceofYieldsFalse:
			case IProblem.RedundantNullCheckOnNonNullExpression:
			case IProblem.NonNullExpressionComparisonYieldsFalse:
			case IProblem.RedundantNullCheckOnNonNullMessageSend:
			case IProblem.RedundantNullCheckOnSpecdNonNullLocalVariable:
			case IProblem.SpecdNonNullLocalVariableComparisonYieldsFalse:
			case IProblem.NonNullMessageSendComparisonYieldsFalse:
			case IProblem.RedundantNullCheckOnNonNullSpecdField:
			case IProblem.NonNullSpecdFieldComparisonYieldsFalse:
			case IProblem.RedundantNullCheckAgainstNonNullType:
			case IProblem.RedundantNullCheckOnField:
			case IProblem.FieldComparisonYieldsFalse:
				return CompilerOptions.RedundantNullCheck;
	
			case IProblem.RequiredNonNullButProvidedNull:
			case IProblem.RequiredNonNullButProvidedSpecdNullable:
			case IProblem.IllegalReturnNullityRedefinition:
			case IProblem.IllegalRedefinitionToNonNullParameter:
			case IProblem.IllegalDefinitionToNonNullParameter:
			case IProblem.ParameterLackingNullableAnnotation:
			case IProblem.CannotImplementIncompatibleNullness:
			case IProblem.ConflictingNullAnnotations:
			case IProblem.ConflictingInheritedNullAnnotations:
			case IProblem.NullNotCompatibleToFreeTypeVariable:
			case IProblem.NullityMismatchAgainstFreeTypeVariable:
			case IProblem.NullityMismatchingTypeAnnotation:
			case IProblem.NullityMismatchingTypeAnnotationSuperHint:
			case IProblem.NullityMismatchTypeArgument:
			case IProblem.UninitializedNonNullField:
			case IProblem.UninitializedNonNullFieldHintMissingDefault:
			case IProblem.ReferenceExpressionParameterNullityMismatch:
			case IProblem.ReferenceExpressionReturnNullRedef:
				return CompilerOptions.NullSpecViolation;
	
			case IProblem.ParameterLackingNonNullAnnotation:
				return CompilerOptions.NonnullParameterAnnotationDropped;
	
			case IProblem.RequiredNonNullButProvidedPotentialNull:
				return CompilerOptions.NullAnnotationInferenceConflict;
			case IProblem.RequiredNonNullButProvidedUnknown:
			case IProblem.NullityUncheckedTypeAnnotationDetail:
			case IProblem.NullityUncheckedTypeAnnotationDetailSuperHint:
			case IProblem.ReferenceExpressionParameterNullityUnchecked:
			case IProblem.ReferenceExpressionReturnNullRedefUnchecked:
			case IProblem.UnsafeNullnessCast:
				return CompilerOptions.NullUncheckedConversion;
			case IProblem.RedundantNullAnnotation:
			case IProblem.RedundantNullDefaultAnnotation:
			case IProblem.RedundantNullDefaultAnnotationPackage:
			case IProblem.RedundantNullDefaultAnnotationType:
			case IProblem.RedundantNullDefaultAnnotationMethod:
				return CompilerOptions.RedundantNullAnnotation;
	
			case IProblem.BoxingConversion :
			case IProblem.UnboxingConversion :
				return CompilerOptions.AutoBoxing;
	
			case IProblem.MissingEnumConstantCase :
			case IProblem.MissingEnumConstantCaseDespiteDefault :	// this one is further protected by CompilerOptions.reportMissingEnumCaseDespiteDefault
				return CompilerOptions.MissingEnumConstantCase;
	
			case IProblem.MissingDefaultCase :
			case IProblem.MissingEnumDefaultCase :
				return CompilerOptions.MissingDefaultCase;
	
			case IProblem.AnnotationTypeUsedAsSuperInterface :
				return CompilerOptions.AnnotationSuperInterface;
	
			case IProblem.UnhandledWarningToken :
				return CompilerOptions.UnhandledWarningToken;
	
			case IProblem.UnusedWarningToken :
				return CompilerOptions.UnusedWarningToken;
	
			case IProblem.UnusedLabel :
				return CompilerOptions.UnusedLabel;
	
			case IProblem.JavadocUnexpectedTag:
			case IProblem.JavadocDuplicateTag:
			case IProblem.JavadocDuplicateReturnTag:
			case IProblem.JavadocInvalidThrowsClass:
			case IProblem.JavadocInvalidSeeReference:
			case IProblem.JavadocInvalidParamTagName:
			case IProblem.JavadocInvalidParamTagTypeParameter:
			case IProblem.JavadocMalformedSeeReference:
			case IProblem.JavadocInvalidSeeHref:
			case IProblem.JavadocInvalidSeeArgs:
			case IProblem.JavadocInvalidTag:
			case IProblem.JavadocUnterminatedInlineTag:
			case IProblem.JavadocMissingHashCharacter:
			case IProblem.JavadocEmptyReturnTag:
			case IProblem.JavadocUnexpectedText:
			case IProblem.JavadocInvalidParamName:
			case IProblem.JavadocDuplicateParamName:
			case IProblem.JavadocMissingParamName:
			case IProblem.JavadocMissingIdentifier:
			case IProblem.JavadocInvalidMemberTypeQualification:
			case IProblem.JavadocInvalidThrowsClassName:
			case IProblem.JavadocDuplicateThrowsClassName:
			case IProblem.JavadocMissingThrowsClassName:
			case IProblem.JavadocMissingSeeReference:
			case IProblem.JavadocInvalidValueReference:
			case IProblem.JavadocUndefinedField:
			case IProblem.JavadocAmbiguousField:
			case IProblem.JavadocUndefinedConstructor:
			case IProblem.JavadocAmbiguousConstructor:
			case IProblem.JavadocUndefinedMethod:
			case IProblem.JavadocAmbiguousMethod:
			case IProblem.JavadocAmbiguousMethodReference:
			case IProblem.JavadocParameterMismatch:
			case IProblem.JavadocUndefinedType:
			case IProblem.JavadocAmbiguousType:
			case IProblem.JavadocInternalTypeNameProvided:
			case IProblem.JavadocNoMessageSendOnArrayType:
			case IProblem.JavadocNoMessageSendOnBaseType:
			case IProblem.JavadocInheritedMethodHidesEnclosingName:
			case IProblem.JavadocInheritedFieldHidesEnclosingName:
			case IProblem.JavadocInheritedNameHidesEnclosingTypeName:
			case IProblem.JavadocNonStaticTypeFromStaticInvocation:
			case IProblem.JavadocGenericMethodTypeArgumentMismatch:
			case IProblem.JavadocNonGenericMethod:
			case IProblem.JavadocIncorrectArityForParameterizedMethod:
			case IProblem.JavadocParameterizedMethodArgumentTypeMismatch:
			case IProblem.JavadocTypeArgumentsForRawGenericMethod:
			case IProblem.JavadocGenericConstructorTypeArgumentMismatch:
			case IProblem.JavadocNonGenericConstructor:
			case IProblem.JavadocIncorrectArityForParameterizedConstructor:
			case IProblem.JavadocParameterizedConstructorArgumentTypeMismatch:
			case IProblem.JavadocTypeArgumentsForRawGenericConstructor:
			case IProblem.JavadocNotVisibleField:
			case IProblem.JavadocNotVisibleConstructor:
			case IProblem.JavadocNotVisibleMethod:
			case IProblem.JavadocNotVisibleType:
			case IProblem.JavadocUsingDeprecatedField:
			case IProblem.JavadocUsingDeprecatedConstructor:
			case IProblem.JavadocUsingDeprecatedMethod:
			case IProblem.JavadocUsingDeprecatedType:
			case IProblem.JavadocHiddenReference:
			case IProblem.JavadocMissingTagDescription:
			case IProblem.JavadocInvalidSeeUrlReference:
				return CompilerOptions.InvalidJavadoc;
	
			case IProblem.JavadocMissingParamTag:
			case IProblem.JavadocMissingReturnTag:
			case IProblem.JavadocMissingThrowsTag:
				return CompilerOptions.MissingJavadocTags;
	
			case IProblem.JavadocMissing:
				return CompilerOptions.MissingJavadocComments;
	
			case IProblem.ParameterAssignment:
				return CompilerOptions.ParameterAssignment;
	
			case IProblem.FallthroughCase:
				return CompilerOptions.FallthroughCase;
	
			case IProblem.OverridingMethodWithoutSuperInvocation:
				return CompilerOptions.OverridingMethodWithoutSuperInvocation;
	
			case IProblem.UnusedTypeArgumentsForMethodInvocation:
			case IProblem.UnusedTypeArgumentsForConstructorInvocation:
				return CompilerOptions.UnusedTypeArguments;
	
			case IProblem.RedundantSuperinterface:
				return CompilerOptions.RedundantSuperinterface;
	
			case IProblem.ComparingIdentical:
				return CompilerOptions.ComparingIdentical;
				
			case IProblem.MissingSynchronizedModifierInInheritedMethod:
				return CompilerOptions.MissingSynchronizedModifierInInheritedMethod;
	
			case IProblem.ShouldImplementHashcode:
				return CompilerOptions.ShouldImplementHashcode;
				
			case IProblem.DeadCode:
				return CompilerOptions.DeadCode;
				
			case IProblem.Task :
				return CompilerOptions.Tasks;
	
			case IProblem.UnusedObjectAllocation:
				return CompilerOptions.UnusedObjectAllocation;
				
			case IProblem.MethodCanBeStatic:
				return CompilerOptions.MethodCanBeStatic;
				
			case IProblem.MethodCanBePotentiallyStatic:
				return CompilerOptions.MethodCanBePotentiallyStatic;
	
			case IProblem.UnclosedCloseable:
			case IProblem.UnclosedCloseableAtExit:
				return CompilerOptions.UnclosedCloseable;
			case IProblem.PotentiallyUnclosedCloseable:
			case IProblem.PotentiallyUnclosedCloseableAtExit:
				return CompilerOptions.PotentiallyUnclosedCloseable;
			case IProblem.ExplicitlyClosedAutoCloseable:
				return CompilerOptions.ExplicitlyClosedAutoCloseable;
					
			case IProblem.RedundantSpecificationOfTypeArguments:
				return CompilerOptions.RedundantSpecificationOfTypeArguments;
				
			case IProblem.MissingNonNullByDefaultAnnotationOnPackage:
			case IProblem.MissingNonNullByDefaultAnnotationOnType:
				return CompilerOptions.MissingNonNullByDefaultAnnotation;
				
			case IProblem.UnusedTypeParameter:
				return CompilerOptions.UnusedTypeParameter;
	}
		return 0;
	}
	/**
	 * Compute problem category ID based on problem ID
	 * @param problemID
	 * @return a category ID
	 * @see CategorizedProblem
	 */
	public static int getProblemCategory(int severity, int problemID) {
		categorizeOnIrritant: {
			// fatal problems even if optional are all falling into same category (not irritant based)
			if ((severity & ProblemSeverities.Fatal) != 0)
				break categorizeOnIrritant;
			int irritant = getIrritant(problemID);
			switch (irritant) {
				case CompilerOptions.MethodWithConstructorName :
				case CompilerOptions.AccessEmulation :
				case CompilerOptions.AssertUsedAsAnIdentifier :
				case CompilerOptions.NonStaticAccessToStatic :
				case CompilerOptions.UnqualifiedFieldAccess :
				case CompilerOptions.UndocumentedEmptyBlock :
				case CompilerOptions.IndirectStaticAccess :
				case CompilerOptions.FinalParameterBound :
				case CompilerOptions.EnumUsedAsAnIdentifier :
				case CompilerOptions.AnnotationSuperInterface :
				case CompilerOptions.AutoBoxing :
				case CompilerOptions.MissingOverrideAnnotation :
				case CompilerOptions.MissingDeprecatedAnnotation :
				case CompilerOptions.ParameterAssignment :
				case CompilerOptions.MethodCanBeStatic :
				case CompilerOptions.MethodCanBePotentiallyStatic :
				case CompilerOptions.ExplicitlyClosedAutoCloseable :
					return CategorizedProblem.CAT_CODE_STYLE;
	
				case CompilerOptions.MaskedCatchBlock :
				case CompilerOptions.NoImplicitStringConversion :
				case CompilerOptions.NoEffectAssignment :
				case CompilerOptions.AccidentalBooleanAssign :
				case CompilerOptions.EmptyStatement :
				case CompilerOptions.FinallyBlockNotCompleting :
				case CompilerOptions.MissingSerialVersion :
				case CompilerOptions.VarargsArgumentNeedCast :
				case CompilerOptions.NullReference :
				case CompilerOptions.PotentialNullReference :
				case CompilerOptions.RedundantNullCheck :
				case CompilerOptions.MissingEnumConstantCase :
				case CompilerOptions.MissingDefaultCase :
				case CompilerOptions.FallthroughCase :
				case CompilerOptions.OverridingMethodWithoutSuperInvocation :
				case CompilerOptions.ComparingIdentical :
				case CompilerOptions.MissingSynchronizedModifierInInheritedMethod :
				case CompilerOptions.ShouldImplementHashcode :
				case CompilerOptions.DeadCode :
				case CompilerOptions.UnusedObjectAllocation :
				case CompilerOptions.UnclosedCloseable :
				case CompilerOptions.PotentiallyUnclosedCloseable :
					return CategorizedProblem.CAT_POTENTIAL_PROGRAMMING_PROBLEM;
				
				case CompilerOptions.OverriddenPackageDefaultMethod :
				case CompilerOptions.IncompatibleNonInheritedInterfaceMethod :
				case CompilerOptions.LocalVariableHiding :
				case CompilerOptions.FieldHiding :
				case CompilerOptions.TypeHiding :
					return CategorizedProblem.CAT_NAME_SHADOWING_CONFLICT;
	
				case CompilerOptions.UnusedLocalVariable :
				case CompilerOptions.UnusedArgument :
				case CompilerOptions.UnusedImport :
				case CompilerOptions.UnusedPrivateMember :
				case CompilerOptions.UnusedDeclaredThrownException :
				case CompilerOptions.UnnecessaryTypeCheck :
				case CompilerOptions.UnnecessaryElse :
				case CompilerOptions.UnhandledWarningToken :
				case CompilerOptions.UnusedWarningToken :
				case CompilerOptions.UnusedLabel :
				case CompilerOptions.RedundantSuperinterface :
				case CompilerOptions.RedundantSpecificationOfTypeArguments :
				case CompilerOptions.UnusedTypeParameter:
					return CategorizedProblem.CAT_UNNECESSARY_CODE;
	
				case CompilerOptions.UsingDeprecatedAPI :
					return CategorizedProblem.CAT_DEPRECATION;
	
				case CompilerOptions.NonExternalizedString :
					return CategorizedProblem.CAT_NLS;
	
				case CompilerOptions.Task :
					return CategorizedProblem.CAT_UNSPECIFIED; // TODO may want to improve
				
				case CompilerOptions.MissingJavadocComments :
				case CompilerOptions.MissingJavadocTags :
				case CompilerOptions.InvalidJavadoc :
				case CompilerOptions.InvalidJavadoc|CompilerOptions.UsingDeprecatedAPI :
					return CategorizedProblem.CAT_JAVADOC;
	
				case CompilerOptions.UncheckedTypeOperation :
				case CompilerOptions.RawTypeReference :
					return CategorizedProblem.CAT_UNCHECKED_RAW;
				
				case CompilerOptions.ForbiddenReference :
				case CompilerOptions.DiscouragedReference :
					return CategorizedProblem.CAT_RESTRICTION;
	
				case CompilerOptions.NullSpecViolation :
				case CompilerOptions.NullAnnotationInferenceConflict :
				case CompilerOptions.NullUncheckedConversion :
				case CompilerOptions.MissingNonNullByDefaultAnnotation:
				case CompilerOptions.NonnullParameterAnnotationDropped:
					return CategorizedProblem.CAT_POTENTIAL_PROGRAMMING_PROBLEM;
				case CompilerOptions.RedundantNullAnnotation :
					return CategorizedProblem.CAT_UNNECESSARY_CODE;
	
				default:
					break categorizeOnIrritant;
			}
		}
		// categorize fatal problems per ID
		switch (problemID) {
			case IProblem.IsClassPathCorrect :
			case IProblem.CorruptedSignature :
				return CategorizedProblem.CAT_BUILDPATH;
	
			default :
				if ((problemID & IProblem.Syntax) != 0)
					return CategorizedProblem.CAT_SYNTAX;
				if ((problemID & IProblem.ImportRelated) != 0)
					return CategorizedProblem.CAT_IMPORT;
				if ((problemID & IProblem.TypeRelated) != 0)
					return CategorizedProblem.CAT_TYPE;
				if ((problemID & (IProblem.FieldRelated|IProblem.MethodRelated|IProblem.ConstructorRelated)) != 0)
					return CategorizedProblem.CAT_MEMBER;
		}
		return CategorizedProblem.CAT_INTERNAL;
	}
	public void abortDueToInternalError(String errorMessage) {
		this.abortDueToInternalError(errorMessage, null);
	}
	public void abortDueToInternalError(String errorMessage, ASTNode location) {
		String[] arguments = new String[] {errorMessage};
		this.handle(
			IProblem.Unclassified,
			arguments,
			arguments,
			ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
			location == null ? 0 : location.sourceStart,
			location == null ? 0 : location.sourceEnd);
	}
	public void abstractMethodCannotBeOverridden(SourceTypeBinding type, MethodBinding concreteMethod) {
	
		this.handle(
			// %1 must be abstract since it cannot override the inherited package-private abstract method %2
			IProblem.AbstractMethodCannotBeOverridden,
			new String[] {
				new String(type.sourceName()),
				new String(
						CharOperation.concat(
							concreteMethod.declaringClass.readableName(),
							concreteMethod.readableName(),
							'.'))},
			new String[] {
				new String(type.sourceName()),
				new String(
						CharOperation.concat(
							concreteMethod.declaringClass.shortReadableName(),
							concreteMethod.shortReadableName(),
							'.'))},
			type.sourceStart(),
			type.sourceEnd());
	}
	public void abstractMethodInAbstractClass(SourceTypeBinding type, AbstractMethodDeclaration methodDecl) {
		if (type.isEnum() && type.isLocalType()) {
			FieldBinding field = type.scope.enclosingMethodScope().initializedField;
			FieldDeclaration decl = field.sourceField();
			String[] arguments = new String[] {new String(decl.name), new String(methodDecl.selector)};
			this.handle(
				IProblem.AbstractMethodInEnum,
				arguments,
				arguments,
				methodDecl.sourceStart,
				methodDecl.sourceEnd);
		} else {
			String[] arguments = new String[] {new String(type.sourceName()), new String(methodDecl.selector)};
			this.handle(
				IProblem.AbstractMethodInAbstractClass,
				arguments,
				arguments,
				methodDecl.sourceStart,
				methodDecl.sourceEnd);
		}
	}
	public void abstractMethodInConcreteClass(SourceTypeBinding type) {
		if (type.isEnum() && type.isLocalType()) {
			FieldBinding field = type.scope.enclosingMethodScope().initializedField;
			FieldDeclaration decl = field.sourceField();
			String[] arguments = new String[] {new String(decl.name)};
			this.handle(
				IProblem.EnumConstantCannotDefineAbstractMethod,
				arguments,
				arguments,
				decl.sourceStart(),
				decl.sourceEnd());
		} else {
			String[] arguments = new String[] {new String(type.sourceName())};
			this.handle(
				IProblem.AbstractMethodsInConcreteClass,
				arguments,
				arguments,
				type.sourceStart(),
				type.sourceEnd());
		}
	}
	public void abstractMethodMustBeImplemented(SourceTypeBinding type, MethodBinding abstractMethod) {
		if (type.isEnum() && type.isLocalType()) {
			FieldBinding field = type.scope.enclosingMethodScope().initializedField;
			FieldDeclaration decl = field.sourceField();
			this.handle(
				// Must implement the inherited abstract method %1
				// 8.4.3 - Every non-abstract subclass of an abstract type, A, must provide a concrete implementation of all of A's methods.
				IProblem.EnumConstantMustImplementAbstractMethod,
				new String[] {
				        new String(abstractMethod.selector),
				        typesAsString(abstractMethod, false),
				        new String(decl.name),
				},
				new String[] {
				        new String(abstractMethod.selector),
				        typesAsString(abstractMethod, true),
				        new String(decl.name),
				},
				decl.sourceStart(),
				decl.sourceEnd());
		} else {
			this.handle(
				// Must implement the inherited abstract method %1
				// 8.4.3 - Every non-abstract subclass of an abstract type, A, must provide a concrete implementation of all of A's methods.
				IProblem.AbstractMethodMustBeImplemented,
				new String[] {
				        new String(abstractMethod.selector),
				        typesAsString(abstractMethod, false),
				        new String(abstractMethod.declaringClass.readableName()),
				        new String(type.readableName()),
				},
				new String[] {
				        new String(abstractMethod.selector),
				        typesAsString(abstractMethod, true),
				        new String(abstractMethod.declaringClass.shortReadableName()),
				        new String(type.shortReadableName()),
				},
				type.sourceStart(),
				type.sourceEnd());
		}
	}
	public void abstractMethodMustBeImplemented(SourceTypeBinding type, MethodBinding abstractMethod, MethodBinding concreteMethod) {
		this.handle(
			// Must implement the inherited abstract method %1
			// 8.4.3 - Every non-abstract subclass of an abstract type, A, must provide a concrete implementation of all of A's methods.
			IProblem.AbstractMethodMustBeImplementedOverConcreteMethod,
			new String[] {
			        new String(abstractMethod.selector),
			        typesAsString(abstractMethod, false),
			        new String(abstractMethod.declaringClass.readableName()),
			        new String(type.readableName()),
			        new String(concreteMethod.selector),
			        typesAsString(concreteMethod, false),
			        new String(concreteMethod.declaringClass.readableName()),
			},
			new String[] {
			        new String(abstractMethod.selector),
			        typesAsString(abstractMethod, true),
			        new String(abstractMethod.declaringClass.shortReadableName()),
			        new String(type.shortReadableName()),
			        new String(concreteMethod.selector),
			        typesAsString(concreteMethod, true),
			        new String(concreteMethod.declaringClass.shortReadableName()),
			},
			type.sourceStart(),
			type.sourceEnd());
	}
	public void abstractMethodNeedingNoBody(AbstractMethodDeclaration method) {
		this.handle(
			IProblem.BodyForAbstractMethod,
			NoArgument,
			NoArgument,
			method.sourceStart,
			method.sourceEnd,
			method,
			method.compilationResult());
	}
	public void alreadyDefinedLabel(char[] labelName, ASTNode location) {
		String[] arguments = new String[] {new String(labelName)};
		this.handle(
			IProblem.DuplicateLabel,
			arguments,
			arguments,
			location.sourceStart,
			location.sourceEnd);
	}
	public void annotationCannotOverrideMethod(MethodBinding overrideMethod, MethodBinding inheritedMethod) {
		ASTNode location = overrideMethod.sourceMethod();
		this.handle(
			IProblem.AnnotationCannotOverrideMethod,
			new String[] {
					new String(overrideMethod.declaringClass.readableName()),
					new String(inheritedMethod.declaringClass.readableName()),
					new String(inheritedMethod.selector),
					typesAsString(inheritedMethod, false)},
			new String[] {
					new String(overrideMethod.declaringClass.shortReadableName()),
					new String(inheritedMethod.declaringClass.shortReadableName()),
					new String(inheritedMethod.selector),
					typesAsString(inheritedMethod, true)},
			location.sourceStart,
			location.sourceEnd);
	}
	public void annotationCircularity(TypeBinding sourceType, TypeBinding otherType, TypeReference reference) {
		if (TypeBinding.equalsEquals(sourceType, otherType))
			this.handle(
				IProblem.AnnotationCircularitySelfReference,
				new String[] {new String(sourceType.readableName())},
				new String[] {new String(sourceType.shortReadableName())},
				reference.sourceStart,
				reference.sourceEnd);
		else
			this.handle(
				IProblem.AnnotationCircularity,
				new String[] {new String(sourceType.readableName()), new String(otherType.readableName())},
				new String[] {new String(sourceType.shortReadableName()), new String(otherType.shortReadableName())},
				reference.sourceStart,
				reference.sourceEnd);
	}
	public void annotationMembersCannotHaveParameters(AnnotationMethodDeclaration annotationMethodDeclaration) {
		this.handle(
			IProblem.AnnotationMembersCannotHaveParameters,
			NoArgument,
			NoArgument,
			annotationMethodDeclaration.sourceStart,
			annotationMethodDeclaration.sourceEnd);
	}
	public void annotationMembersCannotHaveTypeParameters(AnnotationMethodDeclaration annotationMethodDeclaration) {
		this.handle(
			IProblem.AnnotationMembersCannotHaveTypeParameters,
			NoArgument,
			NoArgument,
			annotationMethodDeclaration.sourceStart,
			annotationMethodDeclaration.sourceEnd);
	}
	public void annotationTypeDeclarationCannotHaveConstructor(ConstructorDeclaration constructorDeclaration) {
		this.handle(
			IProblem.AnnotationTypeDeclarationCannotHaveConstructor,
			NoArgument,
			NoArgument,
			constructorDeclaration.sourceStart,
			constructorDeclaration.sourceEnd);
	}
	public void annotationTypeDeclarationCannotHaveSuperclass(TypeDeclaration typeDeclaration) {
		this.handle(
			IProblem.AnnotationTypeDeclarationCannotHaveSuperclass,
			NoArgument,
			NoArgument,
			typeDeclaration.sourceStart,
			typeDeclaration.sourceEnd);
	}
	public void annotationTypeDeclarationCannotHaveSuperinterfaces(TypeDeclaration typeDeclaration) {
		this.handle(
			IProblem.AnnotationTypeDeclarationCannotHaveSuperinterfaces,
			NoArgument,
			NoArgument,
			typeDeclaration.sourceStart,
			typeDeclaration.sourceEnd);
	}
	public void annotationTypeUsedAsSuperinterface(SourceTypeBinding type, TypeReference superInterfaceRef, ReferenceBinding superType) {
		this.handle(
			IProblem.AnnotationTypeUsedAsSuperInterface,
			new String[] {new String(superType.readableName()), new String(type.sourceName())},
			new String[] {new String(superType.shortReadableName()), new String(type.sourceName())},
			superInterfaceRef.sourceStart,
			superInterfaceRef.sourceEnd);
	}
	public void annotationValueMustBeAnnotation(TypeBinding annotationType, char[] name, Expression value, TypeBinding expectedType) {
		String str = new String(name);
		this.handle(
			IProblem.AnnotationValueMustBeAnnotation,
			new String[] { new String(annotationType.readableName()), str, new String(expectedType.readableName()),  },
			new String[] { new String(annotationType.shortReadableName()), str, new String(expectedType.readableName()), },
			value.sourceStart,
			value.sourceEnd);
	}
	public void annotationValueMustBeArrayInitializer(TypeBinding annotationType, char[] name, Expression value) {
		String str = new String(name);
		this.handle(
	    	IProblem.AnnotationValueMustBeArrayInitializer,
			new String[] { new String(annotationType.readableName()), str },
			new String[] { new String(annotationType.shortReadableName()), str},
	    	value.sourceStart,
	    	value.sourceEnd);
	}
	public void annotationValueMustBeClassLiteral(TypeBinding annotationType, char[] name, Expression value) {
		String str = new String(name);
		this.handle(
			IProblem.AnnotationValueMustBeClassLiteral,
			new String[] { new String(annotationType.readableName()), str },
			new String[] { new String(annotationType.shortReadableName()), str},
			value.sourceStart,
			value.sourceEnd);
	}
	public void annotationValueMustBeConstant(TypeBinding annotationType, char[] name, Expression value, boolean isEnum) {
		String str = new String(name);
		if (isEnum) {
	    	this.handle(
	    		IProblem.AnnotationValueMustBeAnEnumConstant,
	    		new String[] { new String(annotationType.readableName()), str },
	    		new String[] { new String(annotationType.shortReadableName()), str},
	    		value.sourceStart,
	    		value.sourceEnd);
		} else {
	    	this.handle(
	    		IProblem.AnnotationValueMustBeConstant,
	    		new String[] { new String(annotationType.readableName()), str },
	    		new String[] { new String(annotationType.shortReadableName()), str},
	    		value.sourceStart,
	    		value.sourceEnd);
	    }
	}
	public void anonymousClassCannotExtendFinalClass(TypeReference reference, TypeBinding type) {
		this.handle(
			IProblem.AnonymousClassCannotExtendFinalClass,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void argumentTypeCannotBeVoid(ASTNode methodDecl, Argument arg) {
		String[] arguments = new String[] { new String(arg.name) };
		this.handle(
			IProblem.ArgumentTypeCannotBeVoid,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void argumentTypeCannotBeVoidArray(Argument arg) {
		this.handle(
			IProblem.CannotAllocateVoidArray,
			NoArgument,
			NoArgument,
			arg.type.sourceStart,
			arg.type.sourceEnd);
	}
	public void arrayConstantsOnlyInArrayInitializers(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.ArrayConstantsOnlyInArrayInitializers,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void assignmentHasNoEffect(AbstractVariableDeclaration location, char[] name){
		int severity = computeSeverity(IProblem.AssignmentHasNoEffect);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] { new String(name) };
		int start = location.sourceStart;
		int end = location.sourceEnd;
		if (location.initialization != null) {
			end = location.initialization.sourceEnd;
		}
		this.handle(
				IProblem.AssignmentHasNoEffect,
				arguments,
				arguments,
				severity,
				start,
				end);
	}
	public void assignmentHasNoEffect(Assignment location, char[] name){
		int severity = computeSeverity(IProblem.AssignmentHasNoEffect);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] { new String(name) };
		this.handle(
				IProblem.AssignmentHasNoEffect,
				arguments,
				arguments,
				severity,
				location.sourceStart,
				location.sourceEnd);
	}
	
	public void attemptToReturnNonVoidExpression(ReturnStatement returnStatement, TypeBinding expectedType) {
		this.handle(
			IProblem.VoidMethodReturnsValue,
			new String[] {new String(expectedType.readableName())},
			new String[] {new String(expectedType.shortReadableName())},
			returnStatement.sourceStart,
			returnStatement.sourceEnd);
	}
	
	
	public void attemptToReturnVoidValue(ReturnStatement returnStatement) {
		this.handle(
			IProblem.MethodReturnsVoid,
			NoArgument,
			NoArgument,
			returnStatement.sourceStart,
			returnStatement.sourceEnd);
	}
	public void autoboxing(Expression expression, TypeBinding originalType, TypeBinding convertedType) {
		if (this.options.getSeverity(CompilerOptions.AutoBoxing) == ProblemSeverities.Ignore) return;
		this.handle(
			originalType.isBaseType() ? IProblem.BoxingConversion : IProblem.UnboxingConversion,
			new String[] { new String(originalType.readableName()), new String(convertedType.readableName()), },
			new String[] { new String(originalType.shortReadableName()), new String(convertedType.shortReadableName()), },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void boundCannotBeArray(ASTNode location, TypeBinding type) {
		this.handle(
			IProblem.BoundCannotBeArray,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void boundMustBeAnInterface(ASTNode location, TypeBinding type) {
		this.handle(
			IProblem.BoundMustBeAnInterface,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void bytecodeExceeds64KLimit(AbstractMethodDeclaration location) {
		MethodBinding method = location.binding;
		if (location.isConstructor()) {
			this.handle(
				IProblem.BytecodeExceeds64KLimitForConstructor,
				new String[] {new String(location.selector), typesAsString(method, false)},
				new String[] {new String(location.selector), typesAsString(method, true)},
				ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
				location.sourceStart,
				location.sourceEnd);
		} else {
			this.handle(
				IProblem.BytecodeExceeds64KLimit,
				new String[] {new String(location.selector), typesAsString(method, false)},
				new String[] {new String(location.selector), typesAsString(method, true)},
				ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
				location.sourceStart,
				location.sourceEnd);
		}
	}
	public void bytecodeExceeds64KLimit(LambdaExpression location) {
		MethodBinding method = location.binding;
			this.handle(
				IProblem.BytecodeExceeds64KLimit,
				new String[] {new String(method.selector), typesAsString(method, false)},
				new String[] {new String(method.selector), typesAsString(method, true)},
				ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
				location.sourceStart,
				location.diagnosticsSourceEnd());
	}
	public void bytecodeExceeds64KLimit(TypeDeclaration location) {
		this.handle(
			IProblem.BytecodeExceeds64KLimitForClinit,
			NoArgument,
			NoArgument,
			ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
			location.sourceStart,
			location.sourceEnd);
	}
	public void cannotAllocateVoidArray(Expression expression) {
		this.handle(
			IProblem.CannotAllocateVoidArray,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void cannotAssignToFinalField(FieldBinding field, ASTNode location) {
		this.handle(
			IProblem.FinalFieldAssignment,
			new String[] {
				(field.declaringClass == null ? "array" : new String(field.declaringClass.readableName())), //$NON-NLS-1$
				new String(field.readableName())},
			new String[] {
				(field.declaringClass == null ? "array" : new String(field.declaringClass.shortReadableName())), //$NON-NLS-1$
				new String(field.shortReadableName())},
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	public void cannotAssignToFinalLocal(LocalVariableBinding local, ASTNode location) {
		int problemId = 0;
		if ((local.tagBits & TagBits.MultiCatchParameter) != 0) {
			problemId = IProblem.AssignmentToMultiCatchParameter;
		} else if ((local.tagBits & TagBits.IsResource) != 0) {
			problemId = IProblem.AssignmentToResource;
		} else {
			problemId = IProblem.NonBlankFinalLocalAssignment;
		}
		String[] arguments = new String[] { new String(local.readableName())};
		this.handle(
			problemId,
			arguments,
			arguments,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void cannotAssignToFinalOuterLocal(LocalVariableBinding local, ASTNode location) {
		String[] arguments = new String[] {new String(local.readableName())};
		this.handle(
			IProblem.FinalOuterLocalAssignment,
			arguments,
			arguments,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void cannotDefineDimensionsAndInitializer(ArrayAllocationExpression expresssion) {
		this.handle(
			IProblem.CannotDefineDimensionExpressionsWithInit,
			NoArgument,
			NoArgument,
			expresssion.sourceStart,
			expresssion.sourceEnd);
	}
	public void cannotDireclyInvokeAbstractMethod(ASTNode invocationSite, MethodBinding method) {
		this.handle(
			IProblem.DirectInvocationOfAbstractMethod,
			new String[] {new String(method.declaringClass.readableName()), new String(method.selector), typesAsString(method, false)},
			new String[] {new String(method.declaringClass.shortReadableName()), new String(method.selector), typesAsString(method, true)},
			invocationSite.sourceStart,
			invocationSite.sourceEnd);
	}
	public void cannotExtendEnum(SourceTypeBinding type, TypeReference superclass, TypeBinding superTypeBinding) {
		String name = new String(type.sourceName());
		String superTypeFullName = new String(superTypeBinding.readableName());
		String superTypeShortName = new String(superTypeBinding.shortReadableName());
		if (superTypeShortName.equals(name)) superTypeShortName = superTypeFullName;
		this.handle(
			IProblem.CannotExtendEnum,
			new String[] {superTypeFullName, name},
			new String[] {superTypeShortName, name},
			superclass.sourceStart,
			superclass.sourceEnd);
	}
	public void cannotImportPackage(ImportReference importRef) {
		String[] arguments = new String[] {CharOperation.toString(importRef.tokens)};
		this.handle(
			IProblem.CannotImportPackage,
			arguments,
			arguments,
			importRef.sourceStart,
			importRef.sourceEnd);
	}
	public void cannotInstantiate(Expression typeRef, TypeBinding type) {
		this.handle(
			IProblem.InvalidClassInstantiation,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			typeRef.sourceStart,
			typeRef.sourceEnd);
	}
	public void cannotInvokeSuperConstructorInEnum(ExplicitConstructorCall constructorCall, MethodBinding enumConstructor) {
		this.handle(
			IProblem.CannotInvokeSuperConstructorInEnum,
			new String[] {
			        new String(enumConstructor.declaringClass.sourceName()),
			        typesAsString(enumConstructor, false),
			 },
			new String[] {
			        new String(enumConstructor.declaringClass.sourceName()),
			        typesAsString(enumConstructor, true),
			 },
			constructorCall.sourceStart,
			constructorCall.sourceEnd);
	}
	public void cannotReadSource(CompilationUnitDeclaration unit, AbortCompilationUnit abortException, boolean verbose) {
		String fileName = new String(unit.compilationResult.fileName);
		if (abortException.exception instanceof CharConversionException) {
			// specific encoding issue
			String encoding = abortException.encoding;
			if (encoding == null) {
				encoding = System.getProperty("file.encoding"); //$NON-NLS-1$
			}
			String[] arguments = new String[]{ fileName, encoding };
			this.handle(
					IProblem.InvalidEncoding,
					arguments,
					arguments,
					0,
					0);
			return;
		}
		StringWriter stringWriter = new StringWriter();
		PrintWriter writer = new PrintWriter(stringWriter);
		if (verbose) {
			abortException.exception.printStackTrace(writer);
			System.err.println(stringWriter.toString());
			stringWriter = new StringWriter();
			writer = new PrintWriter(stringWriter);
		}
		writer.print(abortException.exception.getClass().getName());
		writer.print(':');
		writer.print(abortException.exception.getMessage());
		String exceptionTrace = stringWriter.toString();
		String[] arguments = new String[]{ fileName, exceptionTrace };
		this.handle(
				IProblem.CannotReadSource,
				arguments,
				arguments,
				0,
				0);
	}
	public void cannotReferToNonFinalOuterLocal(LocalVariableBinding local, ASTNode location) {
		String[] arguments =new String[]{ new String(local.readableName())};
		this.handle(
			IProblem.OuterLocalMustBeFinal,
			arguments,
			arguments,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void cannotReferToNonEffectivelyFinalOuterLocal(LocalVariableBinding local, ASTNode location) {
		String[] arguments = new String[] { new String(local.readableName()) };
		this.handle(
			IProblem.OuterLocalMustBeEffectivelyFinal, 
			arguments,
			arguments,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void cannotReturnInInitializer(ASTNode location) {
		this.handle(
			IProblem.CannotReturnInInitializer,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void cannotThrowNull(ASTNode expression) {
		this.handle(
			IProblem.CannotThrowNull,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void cannotThrowType(ASTNode exception, TypeBinding expectedType) {
		this.handle(
			IProblem.CannotThrowType,
			new String[] {new String(expectedType.readableName())},
			new String[] {new String(expectedType.shortReadableName())},
			exception.sourceStart,
			exception.sourceEnd);
	}
	// https://bugs.eclipse.org/bugs/show_bug.cgi?id=391092
	public void illegalArrayOfUnionType(char[] identifierName, TypeReference typeReference) {
			this.handle(
			IProblem.IllegalArrayOfUnionType,
			NoArgument,
			NoArgument,
			typeReference.sourceStart,
			typeReference.sourceEnd);
	}
	public void cannotUseQualifiedEnumConstantInCaseLabel(Reference location, FieldBinding field) {
		this.handle(
			IProblem.IllegalQualifiedEnumConstantLabel,
			new String[]{ String.valueOf(field.declaringClass.readableName()), String.valueOf(field.name) },
			new String[]{ String.valueOf(field.declaringClass.shortReadableName()), String.valueOf(field.name) },
			location.sourceStart(),
			location.sourceEnd());
	}
	public void cannotUseSuperInCodeSnippet(int start, int end) {
		this.handle(
			IProblem.CannotUseSuperInCodeSnippet,
			NoArgument,
			NoArgument,
			ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
			start,
			end);
	}
	public void cannotUseSuperInJavaLangObject(ASTNode reference) {
		this.handle(
			IProblem.ObjectHasNoSuperclass,
			NoArgument,
			NoArgument,
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void targetTypeIsNotAFunctionalInterface(FunctionalExpression target) {
		this.handle(
			IProblem.TargetTypeNotAFunctionalInterface,
			NoArgument,
			NoArgument,
			target.sourceStart,
			target.diagnosticsSourceEnd());
	}
	public void illFormedParameterizationOfFunctionalInterface(FunctionalExpression target) {
		this.handle(
			IProblem.illFormedParameterizationOfFunctionalInterface,
			NoArgument,
			NoArgument,
			target.sourceStart,
			target.diagnosticsSourceEnd());
	}
	public void lambdaSignatureMismatched(LambdaExpression target) {
		this.handle(
			IProblem.lambdaSignatureMismatched,
			new String[] { new String(target.descriptor.readableName()) },
			new String[] { new String(target.descriptor.shortReadableName()) },
			target.sourceStart,
			target.diagnosticsSourceEnd());
	}
	
	public void lambdaParameterTypeMismatched(Argument argument, TypeReference type, TypeBinding expectedParameterType) {
		String name = new String(argument.name);
		String expectedTypeFullName = new String(expectedParameterType.readableName());
		String expectedTypeShortName = new String(expectedParameterType.shortReadableName());
		this.handle(
				expectedParameterType.isTypeVariable() ? IProblem.IncompatibleLambdaParameterType : IProblem.lambdaParameterTypeMismatched,
				new String[] { name, expectedTypeFullName },
				new String[] { name, expectedTypeShortName },
				type.sourceStart,
				type.sourceEnd);
	}
	public void lambdaExpressionCannotImplementGenericMethod(LambdaExpression lambda, MethodBinding sam) {
		final String selector = new String(sam.selector);
		this.handle(
				IProblem.NoGenericLambda, 
				new String[] { selector, new String(sam.declaringClass.readableName())},
				new String[] { selector, new String(sam.declaringClass.shortReadableName())},
				lambda.sourceStart,
				lambda.diagnosticsSourceEnd());
	}
	public void caseExpressionMustBeConstant(Expression expression) {
		this.handle(
			IProblem.NonConstantExpression,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void classExtendFinalClass(SourceTypeBinding type, TypeReference superclass, TypeBinding superTypeBinding) {
		String name = new String(type.sourceName());
		String superTypeFullName = new String(superTypeBinding.readableName());
		String superTypeShortName = new String(superTypeBinding.shortReadableName());
		if (superTypeShortName.equals(name)) superTypeShortName = superTypeFullName;
		this.handle(
			IProblem.ClassExtendFinalClass,
			new String[] {superTypeFullName, name},
			new String[] {superTypeShortName, name},
			superclass.sourceStart,
			superclass.sourceEnd);
	}
	public void codeSnippetMissingClass(String missing, int start, int end) {
		String[] arguments = new String[]{missing};
		this.handle(
			IProblem.CodeSnippetMissingClass,
			arguments,
			arguments,
			ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
			start,
			end);
	}
	public void codeSnippetMissingMethod(String className, String missingMethod, String argumentTypes, int start, int end) {
		String[] arguments = new String[]{ className, missingMethod, argumentTypes };
		this.handle(
			IProblem.CodeSnippetMissingMethod,
			arguments,
			arguments,
			ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
			start,
			end);
	}
	public void comparingIdenticalExpressions(Expression comparison){
		int severity = computeSeverity(IProblem.ComparingIdentical);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
				IProblem.ComparingIdentical,
				NoArgument,
				NoArgument,
				severity,
				comparison.sourceStart,
				comparison.sourceEnd);
	}
	/*
	 * Given the current configuration, answers which category the problem
	 * falls into:
	 *		ProblemSeverities.Error | ProblemSeverities.Warning | ProblemSeverities.Ignore
	 * when different from Ignore, severity can be coupled with ProblemSeverities.Optional
	 * to indicate that this problem is configurable through options
	 */
	public int computeSeverity(int problemID){
	
		switch (problemID) {
			case IProblem.VarargsConflict :
				return ProblemSeverities.Warning;
	 		case IProblem.TypeCollidesWithPackage :
				return ProblemSeverities.Warning;
	
			/*
			 * Javadoc tags resolved references errors
			 */
			case IProblem.JavadocInvalidParamName:
			case IProblem.JavadocDuplicateParamName:
			case IProblem.JavadocMissingParamName:
			case IProblem.JavadocInvalidMemberTypeQualification:
			case IProblem.JavadocInvalidThrowsClassName:
			case IProblem.JavadocDuplicateThrowsClassName:
			case IProblem.JavadocMissingThrowsClassName:
			case IProblem.JavadocMissingSeeReference:
			case IProblem.JavadocInvalidValueReference:
			case IProblem.JavadocUndefinedField:
			case IProblem.JavadocAmbiguousField:
			case IProblem.JavadocUndefinedConstructor:
			case IProblem.JavadocAmbiguousConstructor:
			case IProblem.JavadocUndefinedMethod:
			case IProblem.JavadocAmbiguousMethod:
			case IProblem.JavadocAmbiguousMethodReference:
			case IProblem.JavadocParameterMismatch:
			case IProblem.JavadocUndefinedType:
			case IProblem.JavadocAmbiguousType:
			case IProblem.JavadocInternalTypeNameProvided:
			case IProblem.JavadocNoMessageSendOnArrayType:
			case IProblem.JavadocNoMessageSendOnBaseType:
			case IProblem.JavadocInheritedMethodHidesEnclosingName:
			case IProblem.JavadocInheritedFieldHidesEnclosingName:
			case IProblem.JavadocInheritedNameHidesEnclosingTypeName:
			case IProblem.JavadocNonStaticTypeFromStaticInvocation:
			case IProblem.JavadocGenericMethodTypeArgumentMismatch:
			case IProblem.JavadocNonGenericMethod:
			case IProblem.JavadocIncorrectArityForParameterizedMethod:
			case IProblem.JavadocParameterizedMethodArgumentTypeMismatch:
			case IProblem.JavadocTypeArgumentsForRawGenericMethod:
			case IProblem.JavadocGenericConstructorTypeArgumentMismatch:
			case IProblem.JavadocNonGenericConstructor:
			case IProblem.JavadocIncorrectArityForParameterizedConstructor:
			case IProblem.JavadocParameterizedConstructorArgumentTypeMismatch:
			case IProblem.JavadocTypeArgumentsForRawGenericConstructor:
				if (!this.options.reportInvalidJavadocTags) {
					return ProblemSeverities.Ignore;
				}
				break;
			/*
			 * Javadoc invalid tags due to deprecated references
			 */
			case IProblem.JavadocUsingDeprecatedField:
			case IProblem.JavadocUsingDeprecatedConstructor:
			case IProblem.JavadocUsingDeprecatedMethod:
			case IProblem.JavadocUsingDeprecatedType:
				if (!(this.options.reportInvalidJavadocTags && this.options.reportInvalidJavadocTagsDeprecatedRef)) {
					return ProblemSeverities.Ignore;
				}
				break;
			/*
			 * Javadoc invalid tags due to non-visible references
			 */
			case IProblem.JavadocNotVisibleField:
			case IProblem.JavadocNotVisibleConstructor:
			case IProblem.JavadocNotVisibleMethod:
			case IProblem.JavadocNotVisibleType:
			case IProblem.JavadocHiddenReference:
				if (!(this.options.reportInvalidJavadocTags && this.options.reportInvalidJavadocTagsNotVisibleRef)) {
					return ProblemSeverities.Ignore;
				}
				break;
			/*
			 * Javadoc missing tag descriptions
			 */
			case IProblem.JavadocEmptyReturnTag:
				if (CompilerOptions.NO_TAG.equals(this.options.reportMissingJavadocTagDescription)) {
					return ProblemSeverities.Ignore;
				}
				break;
			case IProblem.JavadocMissingTagDescription:
				if (! CompilerOptions.ALL_STANDARD_TAGS.equals(this.options.reportMissingJavadocTagDescription)) {
					return ProblemSeverities.Ignore;
				}
				break;
			// For compatibility with javac 8b111 for now.	
			case IProblem.RepeatableAnnotationWithRepeatingContainerAnnotation:
			case IProblem.ToleratedMisplacedTypeAnnotations:	
				return ProblemSeverities.Warning;
			case IProblem.IllegalUseOfUnderscoreAsAnIdentifier:
				return this.underScoreIsLambdaParameter ? ProblemSeverities.Error : ProblemSeverities.Warning;
			case IProblem.LambdaShapeComputationError:
				return ProblemSeverities.InternalError;
		}
		int irritant = getIrritant(problemID);
		if (irritant != 0) {
			if ((problemID & IProblem.Javadoc) != 0 && !this.options.docCommentSupport)
				return ProblemSeverities.Ignore;
			return this.options.getSeverity(irritant);
		}
		return ProblemSeverities.Error | ProblemSeverities.Fatal;
	}
	public void conditionalArgumentsIncompatibleTypes(ConditionalExpression expression, TypeBinding trueType, TypeBinding falseType) {
		this.handle(
			IProblem.IncompatibleTypesInConditionalOperator,
			new String[] {new String(trueType.readableName()), new String(falseType.readableName())},
			new String[] {new String(trueType.shortReadableName()), new String(falseType.shortReadableName())},
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void conflictingImport(ImportReference importRef) {
		String[] arguments = new String[] {CharOperation.toString(importRef.tokens)};
		this.handle(
			IProblem.ConflictingImport,
			arguments,
			arguments,
			importRef.sourceStart,
			importRef.sourceEnd);
	}
	public void constantOutOfRange(Literal literal, TypeBinding literalType) {
		String[] arguments = new String[] {new String(literalType.readableName()), new String(literal.source())};
		this.handle(
			IProblem.NumericValueOutOfRange,
			arguments,
			arguments,
			literal.sourceStart,
			literal.sourceEnd);
	}
	public void corruptedSignature(TypeBinding enclosingType, char[] signature, int position) {
		this.handle(
			IProblem.CorruptedSignature,
			new String[] { new String(enclosingType.readableName()), new String(signature), String.valueOf(position) },
			new String[] { new String(enclosingType.shortReadableName()), new String(signature), String.valueOf(position) },
			ProblemSeverities.Error | ProblemSeverities.Abort | ProblemSeverities.Fatal,
			0,
			0);
	}
	public void defaultMethodOverridesObjectMethod(MethodBinding currentMethod) {
		// Java 8 feature
		AbstractMethodDeclaration method = currentMethod.sourceMethod();
		int sourceStart = 0;
		int sourceEnd = 0;
		if (method != null) {
			sourceStart = method.sourceStart;
			sourceEnd = method.sourceEnd;
		}
		this.handle(
			IProblem.DefaultMethodOverridesObjectMethod,
			NoArgument, NoArgument,
			sourceStart, sourceEnd);
	}
	
	public void defaultModifierIllegallySpecified(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.IllegalDefaultModifierSpecification,
			NoArgument, NoArgument,
			sourceStart, sourceEnd);
	}
	
	public void deprecatedField(FieldBinding field, ASTNode location) {
		int severity = computeSeverity(IProblem.UsingDeprecatedField);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.UsingDeprecatedField,
			new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
			new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
			severity,
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	
	public void deprecatedMethod(MethodBinding method, ASTNode location) {
		boolean isConstructor = method.isConstructor();
		int severity = computeSeverity(isConstructor ? IProblem.UsingDeprecatedConstructor : IProblem.UsingDeprecatedMethod);
		if (severity == ProblemSeverities.Ignore) return;
		if (isConstructor) {
			int start = -1;
			if(location instanceof AllocationExpression) {
				// omit the new keyword from the warning marker
				// https://bugs.eclipse.org/bugs/show_bug.cgi?id=300031
				AllocationExpression allocationExpression = (AllocationExpression) location;
				if (allocationExpression.enumConstant != null) {
					start = allocationExpression.enumConstant.sourceStart;
				}
				start = allocationExpression.type.sourceStart;
			}
			this.handle(
				IProblem.UsingDeprecatedConstructor,
				new String[] {new String(method.declaringClass.readableName()), typesAsString(method, false)},
				new String[] {new String(method.declaringClass.shortReadableName()), typesAsString(method, true)},
				severity,
				(start == -1) ? location.sourceStart : start,
				location.sourceEnd);
		} else {
			int start = -1;
			if (location instanceof MessageSend) {
				// start the warning marker from the location where the name of the method starts
				// https://bugs.eclipse.org/bugs/show_bug.cgi?id=300031
				start = (int) (((MessageSend)location).nameSourcePosition >>> 32);
			}
			this.handle(
				IProblem.UsingDeprecatedMethod,
				new String[] {new String(method.declaringClass.readableName()), new String(method.selector), typesAsString(method, false)},
				new String[] {new String(method.declaringClass.shortReadableName()), new String(method.selector), typesAsString(method, true)},
				severity,
				(start == -1) ? location.sourceStart : start,
				location.sourceEnd);
		}
	}
	public void deprecatedType(TypeBinding type, ASTNode location) {
		deprecatedType(type, location, Integer.MAX_VALUE);
	}
	// The argument 'index' makes sure that we demarcate partial types correctly while marking off
	// a deprecated type in a qualified reference (see bug 292510)
	public void deprecatedType(TypeBinding type, ASTNode location, int index) {
		if (location == null) return; // 1G828DN - no type ref for synthetic arguments
		int severity = computeSeverity(IProblem.UsingDeprecatedType);
		if (severity == ProblemSeverities.Ignore) return;
		type = type.leafComponentType();
		int sourceStart = -1;
		if (location instanceof QualifiedTypeReference) { // https://bugs.eclipse.org/bugs/show_bug.cgi?id=300031
			QualifiedTypeReference ref = (QualifiedTypeReference) location;
			if (index < Integer.MAX_VALUE) {
				sourceStart = (int) (ref.sourcePositions[index] >> 32);
			}
		}
		this.handle(
			IProblem.UsingDeprecatedType,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			severity,
			(sourceStart == -1) ? location.sourceStart : sourceStart,
			nodeSourceEnd(null, location, index));
	}
	public void disallowedTargetForAnnotation(Annotation annotation) {
		this.handle(
			IProblem.DisallowedTargetForAnnotation,
			new String[] {new String(annotation.resolvedType.readableName())},
			new String[] {new String(annotation.resolvedType.shortReadableName())},
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	public void explitAnnotationTargetRequired(Annotation annotation) {
		this.handle(IProblem.ExplicitAnnotationTargetRequired,
				NoArgument,
				NoArgument,
				annotation.sourceStart,
				annotation.sourceEnd);
	}
	public void polymorphicMethodNotBelow17(ASTNode node) {
		this.handle(
				IProblem.PolymorphicMethodNotBelow17,
				NoArgument,
				NoArgument,
				node.sourceStart,
				node.sourceEnd);
	}
	public void multiCatchNotBelow17(ASTNode node) {
		this.handle(
				IProblem.MultiCatchNotBelow17,
				NoArgument,
				NoArgument,
				node.sourceStart,
				node.sourceEnd);
	}
	public void duplicateAnnotation(Annotation annotation, long sourceLevel) {
		this.handle(
			sourceLevel >= ClassFileConstants.JDK1_8 ? IProblem.DuplicateAnnotationNotMarkedRepeatable : IProblem.DuplicateAnnotation,
			new String[] {new String(annotation.resolvedType.readableName())},
			new String[] {new String(annotation.resolvedType.shortReadableName())},
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	public void duplicateAnnotationValue(TypeBinding annotationType, MemberValuePair memberValuePair) {
		String name = 	new String(memberValuePair.name);
		this.handle(
			IProblem.DuplicateAnnotationMember,
			new String[] { name, new String(annotationType.readableName())},
			new String[] {	name, new String(annotationType.shortReadableName())},
			memberValuePair.sourceStart,
			memberValuePair.sourceEnd);
	}
	public void duplicateBounds(ASTNode location, TypeBinding type) {
		this.handle(
			IProblem.DuplicateBounds,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void duplicateCase(CaseStatement caseStatement) {
		this.handle(
			IProblem.DuplicateCase,
			NoArgument,
			NoArgument,
			caseStatement.sourceStart,
			caseStatement.sourceEnd);
	}
	public void duplicateDefaultCase(ASTNode statement) {
		this.handle(
			IProblem.DuplicateDefaultCase,
			NoArgument,
			NoArgument,
			statement.sourceStart,
			statement.sourceEnd);
	}
	public void duplicateEnumSpecialMethod(SourceTypeBinding type, AbstractMethodDeclaration methodDecl) {
	    MethodBinding method = methodDecl.binding;
		this.handle(
			IProblem.CannotDeclareEnumSpecialMethod,
			new String[] {
		        new String(methodDecl.selector),
				new String(method.declaringClass.readableName()),
				typesAsString(method, false)},
			new String[] {
				new String(methodDecl.selector),
				new String(method.declaringClass.shortReadableName()),
				typesAsString(method, true)},
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void duplicateFieldInType(SourceTypeBinding type, FieldDeclaration fieldDecl) {
		this.handle(
			IProblem.DuplicateField,
			new String[] {new String(type.sourceName()), new String(fieldDecl.name)},
			new String[] {new String(type.shortReadableName()), new String(fieldDecl.name)},
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void duplicateImport(ImportReference importRef) {
		String[] arguments = new String[] {CharOperation.toString(importRef.tokens)};
		this.handle(
			IProblem.DuplicateImport,
			arguments,
			arguments,
			importRef.sourceStart,
			importRef.sourceEnd);
	}
	
	public void duplicateInheritedMethods(SourceTypeBinding type, MethodBinding inheritedMethod1, MethodBinding inheritedMethod2, boolean isJava8) {
		if (TypeBinding.notEquals(inheritedMethod1.declaringClass, inheritedMethod2.declaringClass)) {
			int problemID = IProblem.DuplicateInheritedMethods;
			if (inheritedMethod1.isDefaultMethod() && inheritedMethod2.isDefaultMethod()) {
				if (isJava8)
					problemID = IProblem.DuplicateInheritedDefaultMethods;
				else
					return; // don't report this error at 1.7-
			}
			this.handle(
				problemID,
				new String[] {
			        new String(inheritedMethod1.selector),
					typesAsString(inheritedMethod1, inheritedMethod1.original().parameters, false),
					typesAsString(inheritedMethod2, inheritedMethod2.original().parameters, false),
					new String(inheritedMethod1.declaringClass.readableName()),
					new String(inheritedMethod2.declaringClass.readableName()),
				},
				new String[] {
					new String(inheritedMethod1.selector),
					typesAsString(inheritedMethod1, inheritedMethod1.original().parameters, true),
					typesAsString(inheritedMethod2, inheritedMethod2.original().parameters, true),
					new String(inheritedMethod1.declaringClass.shortReadableName()),
					new String(inheritedMethod2.declaringClass.shortReadableName()),
				},
				type.sourceStart(),
				type.sourceEnd());
			return;
		}
		// Handle duplicates from same class.
		this.handle(
			IProblem.DuplicateParameterizedMethods,
			new String[] {
		        new String(inheritedMethod1.selector),
				new String(inheritedMethod1.declaringClass.readableName()),
				typesAsString(inheritedMethod1, inheritedMethod1.original().parameters, false),
				typesAsString(inheritedMethod2, inheritedMethod2.original().parameters, false)},
			new String[] {
				new String(inheritedMethod1.selector),
				new String(inheritedMethod1.declaringClass.shortReadableName()),
				typesAsString(inheritedMethod1, inheritedMethod1.original().parameters, true),
				typesAsString(inheritedMethod2, inheritedMethod2.original().parameters, true)},
			type.sourceStart(),
			type.sourceEnd());
	}
	public void duplicateInitializationOfBlankFinalField(FieldBinding field, Reference reference) {
		String[] arguments = new String[]{ new String(field.readableName())};
		this.handle(
			IProblem.DuplicateBlankFinalFieldInitialization,
			arguments,
			arguments,
			nodeSourceStart(field, reference),
			nodeSourceEnd(field, reference));
	}
	public void duplicateInitializationOfFinalLocal(LocalVariableBinding local, ASTNode location) {
		String[] arguments = new String[] { new String(local.readableName())};
		this.handle(
			IProblem.DuplicateFinalLocalInitialization,
			arguments,
			arguments,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void duplicateMethodInType(AbstractMethodDeclaration methodDecl, boolean equalParameters, int severity) {
	    MethodBinding method = methodDecl.binding;
	    if (equalParameters) {
			this.handle(
				IProblem.DuplicateMethod,
				new String[] {
			        new String(methodDecl.selector),
					new String(method.declaringClass.readableName()),
					typesAsString(method, false)},
				new String[] {
					new String(methodDecl.selector),
					new String(method.declaringClass.shortReadableName()),
					typesAsString(method, true)},
				severity,
				methodDecl.sourceStart,
				methodDecl.sourceEnd);
	    } else {
			this.handle(
				IProblem.DuplicateMethodErasure,
				new String[] {
			        new String(methodDecl.selector),
					new String(method.declaringClass.readableName()),
					typesAsString(method, false)},
				new String[] {
					new String(methodDecl.selector),
					new String(method.declaringClass.shortReadableName()),
					typesAsString(method, true)},
				severity,
				methodDecl.sourceStart,
				methodDecl.sourceEnd);
	    }
	}
	
	public void duplicateModifierForField(ReferenceBinding type, FieldDeclaration fieldDecl) {
	/* to highlight modifiers use:
		this.handle(
			new Problem(
				DuplicateModifierForField,
				new String[] {new String(fieldDecl.name)},
				fieldDecl.modifiers.sourceStart,
				fieldDecl.modifiers.sourceEnd));
	*/
		String[] arguments = new String[] {new String(fieldDecl.name)};
		this.handle(
			IProblem.DuplicateModifierForField,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void duplicateModifierForMethod(ReferenceBinding type, AbstractMethodDeclaration methodDecl) {
		this.handle(
			IProblem.DuplicateModifierForMethod,
			new String[] {new String(type.sourceName()), new String(methodDecl.selector)},
			new String[] {new String(type.shortReadableName()), new String(methodDecl.selector)},
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void duplicateModifierForType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.DuplicateModifierForType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void duplicateModifierForVariable(LocalDeclaration localDecl, boolean complainForArgument) {
		String[] arguments = new String[] {new String(localDecl.name)};
		this.handle(
			complainForArgument
				? IProblem.DuplicateModifierForArgument
				: IProblem.DuplicateModifierForVariable,
			arguments,
			arguments,
			localDecl.sourceStart,
			localDecl.sourceEnd);
	}
	public void duplicateNestedType(TypeDeclaration typeDecl) {
		String[] arguments = new String[] {new String(typeDecl.name)};
		this.handle(
			IProblem.DuplicateNestedType,
			arguments,
			arguments,
			typeDecl.sourceStart,
			typeDecl.sourceEnd);
	}
	public void duplicateSuperinterface(SourceTypeBinding type, TypeReference reference, ReferenceBinding superType) {
		this.handle(
			IProblem.DuplicateSuperInterface,
			new String[] {
				new String(superType.readableName()),
				new String(type.sourceName())},
			new String[] {
				new String(superType.shortReadableName()),
				new String(type.sourceName())},
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void duplicateTargetInTargetAnnotation(TypeBinding annotationType, NameReference reference) {
		FieldBinding field = reference.fieldBinding();
		String name = 	new String(field.name);
		this.handle(
			IProblem.DuplicateTargetInTargetAnnotation,
			new String[] { name, new String(annotationType.readableName())},
			new String[] {	name, new String(annotationType.shortReadableName())},
			nodeSourceStart(field, reference),
			nodeSourceEnd(field, reference));
	}
	public void duplicateTypeParameterInType(TypeParameter typeParameter) {
		this.handle(
			IProblem.DuplicateTypeVariable,
			new String[] { new String(typeParameter.name)},
			new String[] { new String(typeParameter.name)},
			typeParameter.sourceStart,
			typeParameter.sourceEnd);
	}
	public void duplicateTypes(CompilationUnitDeclaration compUnitDecl, TypeDeclaration typeDecl) {
		String[] arguments = new String[] {new String(compUnitDecl.getFileName()), new String(typeDecl.name)};
		this.referenceContext = typeDecl; // report the problem against the type not the entire compilation unit
		int end = typeDecl.sourceEnd;
		if (end <= 0) {
			end = -1;
		}
		this.handle(
			IProblem.DuplicateTypes,
			arguments,
			arguments,
			typeDecl.sourceStart,
			end,
			compUnitDecl.compilationResult);
	}
	public void emptyControlFlowStatement(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.EmptyControlFlowStatement,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void enumAbstractMethodMustBeImplemented(AbstractMethodDeclaration method) {
		MethodBinding abstractMethod = method.binding;
		this.handle(
			// Must implement the inherited abstract method %1
			// 8.4.3 - Every non-abstract subclass of an abstract type, A, must provide a concrete implementation of all of A's methods.
			IProblem.EnumAbstractMethodMustBeImplemented,
			new String[] {
			        new String(abstractMethod.selector),
			        typesAsString(abstractMethod, false),
			        new String(abstractMethod.declaringClass.readableName()),
			},
			new String[] {
			        new String(abstractMethod.selector),
			        typesAsString(abstractMethod, true),
			        new String(abstractMethod.declaringClass.shortReadableName()),
			},
			method.sourceStart(),
			method.sourceEnd());
	}
	public void enumConstantMustImplementAbstractMethod(AbstractMethodDeclaration method, FieldDeclaration field) {
		MethodBinding abstractMethod = method.binding;
		this.handle(
			IProblem.EnumConstantMustImplementAbstractMethod,
			new String[] {
			        new String(abstractMethod.selector),
			        typesAsString(abstractMethod, false),
			        new String(field.name),
			},
			new String[] {
			        new String(abstractMethod.selector),
			        typesAsString(abstractMethod, true),
			        new String(field.name),
			},
			field.sourceStart(),
			field.sourceEnd());
	}
	public void enumConstantsCannotBeSurroundedByParenthesis(Expression expression) {
		this.handle(
			IProblem.EnumConstantsCannotBeSurroundedByParenthesis,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void enumStaticFieldUsedDuringInitialization(FieldBinding field, ASTNode location) {
		this.handle(
			IProblem.EnumStaticFieldInInInitializerContext,
			new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
			new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	public void enumSwitchCannotTargetField(Reference reference, FieldBinding field) {
		this.handle(
				IProblem.EnumSwitchCannotTargetField,
				new String[]{ String.valueOf(field.declaringClass.readableName()), String.valueOf(field.name) },
				new String[]{ String.valueOf(field.declaringClass.shortReadableName()), String.valueOf(field.name) },
				nodeSourceStart(field, reference),
				nodeSourceEnd(field, reference));
	}
	public void errorNoMethodFor(MessageSend messageSend, TypeBinding recType, TypeBinding[] params) {
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		for (int i = 0, length = params.length; i < length; i++) {
			if (i != 0){
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(params[i].readableName()));
			shortBuffer.append(new String(params[i].shortReadableName()));
		}
	
		int id = recType.isArrayType() ? IProblem.NoMessageSendOnArrayType : IProblem.NoMessageSendOnBaseType;
		this.handle(
			id,
			new String[] {new String(recType.readableName()), new String(messageSend.selector), buffer.toString()},
			new String[] {new String(recType.shortReadableName()), new String(messageSend.selector), shortBuffer.toString()},
			messageSend.sourceStart,
			messageSend.sourceEnd);
	}
	public void errorNoMethodFor(Expression expression, TypeBinding recType, char [] selector, TypeBinding[] params) {
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		for (int i = 0, length = params.length; i < length; i++) {
			if (i != 0){
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(params[i].readableName()));
			shortBuffer.append(new String(params[i].shortReadableName()));
		}
	
		int id = recType.isArrayType() ? IProblem.NoMessageSendOnArrayType : IProblem.NoMessageSendOnBaseType;
		this.handle(
			id,
			new String[] { new String(recType.readableName()), new String(selector), buffer.toString() },
			new String[] { new String(recType.shortReadableName()), new String(selector), shortBuffer.toString() },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void errorThisSuperInStatic(ASTNode reference) {
		String[] arguments = new String[] {reference.isSuper() ? "super" : "this"}; //$NON-NLS-2$ //$NON-NLS-1$
		this.handle(
			IProblem.ThisInStaticContext,
			arguments,
			arguments,
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void errorNoSuperInInterface(ASTNode reference) {
		this.handle(
			IProblem.NoSuperInInterfaceContext,
			NoArgument,
			NoArgument,
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void expressionShouldBeAVariable(Expression expression) {
		this.handle(
			IProblem.ExpressionShouldBeAVariable,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void fakeReachable(ASTNode location) {
		int sourceStart = location.sourceStart;
		int sourceEnd = location.sourceEnd;
		if (location instanceof LocalDeclaration) {
			LocalDeclaration declaration = (LocalDeclaration) location;
			sourceStart = declaration.declarationSourceStart;
			sourceEnd = declaration.declarationSourceEnd;
		}	
		this.handle(
			IProblem.DeadCode,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void fieldHiding(FieldDeclaration fieldDecl, Binding hiddenVariable) {
		FieldBinding field = fieldDecl.binding;
		if (CharOperation.equals(TypeConstants.SERIALVERSIONUID, field.name)
				&& field.isStatic()
				&& field.isPrivate()
				&& field.isFinal()
				&& TypeBinding.equalsEquals(TypeBinding.LONG, field.type)) {
			ReferenceBinding referenceBinding = field.declaringClass;
			if (referenceBinding != null) {
				if (referenceBinding.findSuperTypeOriginatingFrom(TypeIds.T_JavaIoSerializable, false /*Serializable is not a class*/) != null) {
					return; // do not report field hiding for serialVersionUID field for class that implements Serializable
				}
			}
		}
		if (CharOperation.equals(TypeConstants.SERIALPERSISTENTFIELDS, field.name)
				&& field.isStatic()
				&& field.isPrivate()
				&& field.isFinal()
				&& field.type.dimensions() == 1
				&& CharOperation.equals(TypeConstants.CharArray_JAVA_IO_OBJECTSTREAMFIELD, field.type.leafComponentType().readableName())) {
			ReferenceBinding referenceBinding = field.declaringClass;
			if (referenceBinding != null) {
				if (referenceBinding.findSuperTypeOriginatingFrom(TypeIds.T_JavaIoSerializable, false /*Serializable is not a class*/) != null) {
					return; // do not report field hiding for serialPersistenFields field for class that implements Serializable
				}
			}
		}
		boolean isLocal = hiddenVariable instanceof LocalVariableBinding;
		int severity = computeSeverity(isLocal ? IProblem.FieldHidingLocalVariable : IProblem.FieldHidingField);
		if (severity == ProblemSeverities.Ignore) return;
		if (isLocal) {
			this.handle(
				IProblem.FieldHidingLocalVariable,
				new String[] {new String(field.declaringClass.readableName()), new String(field.name) },
				new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name) },
				severity,
				nodeSourceStart(hiddenVariable, fieldDecl),
				nodeSourceEnd(hiddenVariable, fieldDecl));
		} else if (hiddenVariable instanceof FieldBinding) {
			FieldBinding hiddenField = (FieldBinding) hiddenVariable;
			this.handle(
				IProblem.FieldHidingField,
				new String[] {new String(field.declaringClass.readableName()), new String(field.name) , new String(hiddenField.declaringClass.readableName())  },
				new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name) , new String(hiddenField.declaringClass.shortReadableName()) },
				severity,
				nodeSourceStart(hiddenField, fieldDecl),
				nodeSourceEnd(hiddenField, fieldDecl));
		}
	}
	public void fieldsOrThisBeforeConstructorInvocation(ThisReference reference) {
		this.handle(
			IProblem.ThisSuperDuringConstructorInvocation,
			NoArgument,
			NoArgument,
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void finallyMustCompleteNormally(Block finallyBlock) {
		this.handle(
			IProblem.FinallyMustCompleteNormally,
			NoArgument,
			NoArgument,
			finallyBlock.sourceStart,
			finallyBlock.sourceEnd);
	}
	public void finalMethodCannotBeOverridden(MethodBinding currentMethod, MethodBinding inheritedMethod) {
		this.handle(
			// Cannot override the final method from %1
			// 8.4.3.3 - Final methods cannot be overridden or hidden.
			IProblem.FinalMethodCannotBeOverridden,
			new String[] {new String(inheritedMethod.declaringClass.readableName())},
			new String[] {new String(inheritedMethod.declaringClass.shortReadableName())},
			currentMethod.sourceStart(),
			currentMethod.sourceEnd());
	}
	public void finalVariableBound(TypeVariableBinding typeVariable, TypeReference typeRef) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return;
		int severity = computeSeverity(IProblem.FinalBoundForTypeVariable);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.FinalBoundForTypeVariable,
			new String[] { new String(typeVariable.sourceName()), new String(typeRef.resolvedType.readableName())},
			new String[] { new String(typeVariable.sourceName()), new String(typeRef.resolvedType.shortReadableName())},
			severity,
			typeRef.sourceStart,
			typeRef.sourceEnd);
	}
	/** @param classpathEntryType one of {@link AccessRestriction#COMMAND_LINE},
	 * {@link AccessRestriction#LIBRARY}, {@link AccessRestriction#PROJECT} */
	public void forbiddenReference(FieldBinding field, ASTNode location,
			 byte classpathEntryType, String classpathEntryName, int problemId) {
		int severity = computeSeverity(problemId);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			problemId,
			new String[] { new String(field.readableName()) }, // distinct from msg arg for quickfix purpose
			getElaborationId(IProblem.ForbiddenReference, (byte) (FIELD_ACCESS | classpathEntryType)),
			new String[] {
				classpathEntryName,
				new String(field.shortReadableName()),
		        new String(field.declaringClass.shortReadableName())},
		    severity,
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	/** @param classpathEntryType one of {@link AccessRestriction#COMMAND_LINE},
	 * {@link AccessRestriction#LIBRARY}, {@link AccessRestriction#PROJECT} */
	public void forbiddenReference(MethodBinding method, ASTNode location,
			byte classpathEntryType, String classpathEntryName, int problemId) {
		int severity = computeSeverity(problemId);
		if (severity == ProblemSeverities.Ignore) return;
		if (method.isConstructor())
			this.handle(
				problemId,
				new String[] { new String(method.readableName()) }, // distinct from msg arg for quickfix purpose
				getElaborationId(IProblem.ForbiddenReference, (byte) (CONSTRUCTOR_ACCESS | classpathEntryType)),
				new String[] {
					classpathEntryName,
					new String(method.shortReadableName())},
				severity,
				location.sourceStart,
				location.sourceEnd);
		else
			this.handle(
				problemId,
				new String[] { new String(method.readableName()) }, // distinct from msg arg for quickfix purpose
				getElaborationId(IProblem.ForbiddenReference, (byte) (METHOD_ACCESS | classpathEntryType)),
				new String[] {
					classpathEntryName,
					new String(method.shortReadableName()),
			        new String(method.declaringClass.shortReadableName())},
			    severity,
				location.sourceStart,
				location.sourceEnd);
	}
	/** @param classpathEntryType one of {@link AccessRestriction#COMMAND_LINE},
	 * {@link AccessRestriction#LIBRARY}, {@link AccessRestriction#PROJECT} */
	public void forbiddenReference(TypeBinding type, ASTNode location,
			byte classpathEntryType, String classpathEntryName, int problemId) {
		if (location == null) return;
		int severity = computeSeverity(problemId);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			problemId,
			new String[] { new String(type.readableName()) }, // distinct from msg arg for quickfix purpose
			getElaborationId(IProblem.ForbiddenReference, /* TYPE_ACCESS | */ classpathEntryType), // TYPE_ACCESS values to 0
			new String[] {
				classpathEntryName,
				new String(type.shortReadableName())},
			severity,
			location.sourceStart,
			location.sourceEnd);
	}
	public void forwardReference(Reference reference, int indexInQualification, FieldBinding field) {
		this.handle(
			IProblem.ReferenceToForwardField,
			NoArgument,
			NoArgument,
			nodeSourceStart(field, reference, indexInQualification),
			nodeSourceEnd(field, reference, indexInQualification));
	}
	public void forwardTypeVariableReference(ASTNode location, TypeVariableBinding type) {
		this.handle(
			IProblem.ReferenceToForwardTypeVariable,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void genericTypeCannotExtendThrowable(TypeDeclaration typeDecl) {
		ASTNode location = typeDecl.binding.isAnonymousType() ? typeDecl.allocation.type : typeDecl.superclass;
		this.handle(
			IProblem.GenericTypeCannotExtendThrowable,
			new String[]{ new String(typeDecl.binding.readableName()) },
			new String[]{ new String(typeDecl.binding.shortReadableName()) },
			location.sourceStart,
			location.sourceEnd);
	}
	// use this private API when the compilation unit result can be found through the
	// reference context. Otherwise, use the other API taking a problem and a compilation result
	// as arguments
	private void handle(
			int problemId,
			String[] problemArguments,
			int elaborationId,
			String[] messageArguments,
			int severity,
			int problemStartPosition,
			int problemEndPosition){
		this.handle(
				problemId,
				problemArguments,
				elaborationId,
				messageArguments,
				severity,
				problemStartPosition,
				problemEndPosition,
				this.referenceContext,
				this.referenceContext == null ? null : this.referenceContext.compilationResult());
		this.referenceContext = null;
	}
	// use this private API when the compilation unit result can be found through the
	// reference context. Otherwise, use the other API taking a problem and a compilation result
	// as arguments
	private void handle(
		int problemId,
		String[] problemArguments,
		String[] messageArguments,
		int problemStartPosition,
		int problemEndPosition){
	
		this.handle(
				problemId,
				problemArguments,
				messageArguments,
				problemStartPosition,
				problemEndPosition,
				this.referenceContext,
				this.referenceContext == null ? null : this.referenceContext.compilationResult());
		this.referenceContext = null;
	}
	// use this private API when the compilation unit result cannot be found through the
	// reference context.
	private void handle(
		int problemId,
		String[] problemArguments,
		String[] messageArguments,
		int problemStartPosition,
		int problemEndPosition,
		CompilationResult unitResult){
	
		this.handle(
				problemId,
				problemArguments,
				messageArguments,
				problemStartPosition,
				problemEndPosition,
				this.referenceContext,
				unitResult);
		this.referenceContext = null;
	}
	// use this private API when the compilation unit result can be found through the
	// reference context. Otherwise, use the other API taking a problem and a compilation result
	// as arguments
	private void handle(
		int problemId,
		String[] problemArguments,
		String[] messageArguments,
		int severity,
		int problemStartPosition,
		int problemEndPosition){
	
		this.handle(
				problemId,
				problemArguments,
				0, // no elaboration
				messageArguments,
				severity,
				problemStartPosition,
				problemEndPosition);
	}
	
	public void hiddenCatchBlock(ReferenceBinding exceptionType, ASTNode location) {
		this.handle(
			IProblem.MaskedCatch,
			new String[] {
				new String(exceptionType.readableName()),
			 },
			new String[] {
				new String(exceptionType.shortReadableName()),
			 },
			location.sourceStart,
			location.sourceEnd);
	}
	
	public void hierarchyCircularity(SourceTypeBinding sourceType, ReferenceBinding superType, TypeReference reference) {
		int start = 0;
		int end = 0;
	
		if (reference == null) {	// can only happen when java.lang.Object is busted
			start = sourceType.sourceStart();
			end = sourceType.sourceEnd();
		} else {
			start = reference.sourceStart;
			end = reference.sourceEnd;
		}
	
		if (TypeBinding.equalsEquals(sourceType, superType))
			this.handle(
				IProblem.HierarchyCircularitySelfReference,
				new String[] {new String(sourceType.readableName()) },
				new String[] {new String(sourceType.shortReadableName()) },
				start,
				end);
		else
			this.handle(
				IProblem.HierarchyCircularity,
				new String[] {new String(sourceType.readableName()), new String(superType.readableName())},
				new String[] {new String(sourceType.shortReadableName()), new String(superType.shortReadableName())},
				start,
				end);
	}
	
	public void hierarchyCircularity(TypeVariableBinding type, ReferenceBinding superType, TypeReference reference) {
		int start = 0;
		int end = 0;
	
		start = reference.sourceStart;
		end = reference.sourceEnd;
	
		if (TypeBinding.equalsEquals(type, superType))
			this.handle(
				IProblem.HierarchyCircularitySelfReference,
				new String[] {new String(type.readableName()) },
				new String[] {new String(type.shortReadableName()) },
				start,
				end);
		else
			this.handle(
				IProblem.HierarchyCircularity,
				new String[] {new String(type.readableName()), new String(superType.readableName())},
				new String[] {new String(type.shortReadableName()), new String(superType.shortReadableName())},
				start,
				end);
	}
	
	public void hierarchyHasProblems(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.HierarchyHasProblems,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalAbstractModifierCombinationForMethod(ReferenceBinding type, AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {new String(type.sourceName()), new String(methodDecl.selector)};
		this.handle(
			IProblem.IllegalAbstractModifierCombinationForMethod,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void illegalAbstractModifierCombinationForMethod(AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {new String(methodDecl.selector)};
		this.handle(
			IProblem.IllegalStrictfpForAbstractInterfaceMethod,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void illegalAccessFromTypeVariable(TypeVariableBinding variable, ASTNode location) {
		if ((location.bits & ASTNode.InsideJavadoc)!= 0) {
			javadocInvalidReference(location.sourceStart, location.sourceEnd);
		} else {
			String[] arguments = new String[] { new String(variable.sourceName) };
			this.handle(
					IProblem.IllegalAccessFromTypeVariable,
					arguments,
					arguments,
					location.sourceStart,
					location.sourceEnd);
		}
	}
	public void illegalClassLiteralForTypeVariable(TypeVariableBinding variable, ASTNode location) {
		String[] arguments = new String[] { new String(variable.sourceName) };
		this.handle(
			IProblem.IllegalClassLiteralForTypeVariable,
			arguments,
			arguments,
			location.sourceStart,
			location.sourceEnd);
	}
	public void illegalExtendedDimensions(AnnotationMethodDeclaration annotationTypeMemberDeclaration) {
		this.handle(
			IProblem.IllegalExtendedDimensions,
			NoArgument,
			NoArgument,
			annotationTypeMemberDeclaration.sourceStart,
			annotationTypeMemberDeclaration.sourceEnd);
	}
	public void illegalExtendedDimensions(Argument argument) {
		this.handle(
			IProblem.IllegalExtendedDimensionsForVarArgs,
			NoArgument,
			NoArgument,
			argument.sourceStart,
			argument.sourceEnd);
	}
	public void illegalGenericArray(TypeBinding leafComponentType, ASTNode location) {
		this.handle(
			IProblem.IllegalGenericArray,
			new String[]{ new String(leafComponentType.readableName())},
			new String[]{ new String(leafComponentType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void illegalInstanceOfGenericType(TypeBinding checkedType, ASTNode location) {
		TypeBinding erasedType = checkedType.leafComponentType().erasure();
		StringBuffer recommendedFormBuffer = new StringBuffer(10);
		if (erasedType instanceof ReferenceBinding) {
			ReferenceBinding referenceBinding = (ReferenceBinding) erasedType;
			recommendedFormBuffer.append(referenceBinding.qualifiedSourceName());
		} else {
			recommendedFormBuffer.append(erasedType.sourceName());
		}
		int count = erasedType.typeVariables().length;
		if (count > 0) {
			recommendedFormBuffer.append('<');
			for (int i = 0; i < count; i++) {
				if (i > 0) {
					recommendedFormBuffer.append(',');
				}
				recommendedFormBuffer.append('?');
			}
			recommendedFormBuffer.append('>');
		}
		for (int i = 0, dim = checkedType.dimensions(); i < dim; i++) {
			recommendedFormBuffer.append("[]"); //$NON-NLS-1$
		}
		String recommendedForm = recommendedFormBuffer.toString();
		if (checkedType.leafComponentType().isTypeVariable()) {
			this.handle(
				IProblem.IllegalInstanceofTypeParameter,
				new String[] { new String(checkedType.readableName()), recommendedForm, },
				new String[] { new String(checkedType.shortReadableName()), recommendedForm, },
					location.sourceStart,
					location.sourceEnd);
			return;
		}
		this.handle(
			IProblem.IllegalInstanceofParameterizedType,
			new String[] { new String(checkedType.readableName()), recommendedForm, },
			new String[] { new String(checkedType.shortReadableName()), recommendedForm, },
			location.sourceStart,
			location.sourceEnd);
	}
	public void illegalLocalTypeDeclaration(TypeDeclaration typeDeclaration) {
		if (isRecoveredName(typeDeclaration.name)) return;
	
		int problemID = 0;
		if ((typeDeclaration.modifiers & ClassFileConstants.AccEnum) != 0) {
			problemID = IProblem.CannotDefineEnumInLocalType;
		} else if ((typeDeclaration.modifiers & ClassFileConstants.AccAnnotation) != 0) {
			problemID = IProblem.CannotDefineAnnotationInLocalType;
		} else if ((typeDeclaration.modifiers & ClassFileConstants.AccInterface) != 0) {
			problemID = IProblem.CannotDefineInterfaceInLocalType;
		}
		if (problemID != 0) {
			String[] arguments = new String[] {new String(typeDeclaration.name)};
			this.handle(
				problemID,
				arguments,
				arguments,
				typeDeclaration.sourceStart,
				typeDeclaration.sourceEnd);
		}
	}
	public void illegalModifierCombinationFinalAbstractForClass(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierCombinationFinalAbstractForClass,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierCombinationFinalVolatileForField(ReferenceBinding type, FieldDeclaration fieldDecl) {
		String[] arguments = new String[] {new String(fieldDecl.name)};
	
		this.handle(
			IProblem.IllegalModifierCombinationFinalVolatileForField,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void illegalModifierCombinationForInterfaceMethod(AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {new String(methodDecl.selector)};
		this.handle(
			IProblem.IllegalModifierCombinationForInterfaceMethod,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void illegalModifierForAnnotationField(FieldDeclaration fieldDecl) {
		String name = new String(fieldDecl.name);
		this.handle(
			IProblem.IllegalModifierForAnnotationField,
			new String[] {
				new String(fieldDecl.binding.declaringClass.readableName()),
				name,
			},
			new String[] {
				new String(fieldDecl.binding.declaringClass.shortReadableName()),
				name,
			},
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void illegalModifierForAnnotationMember(AbstractMethodDeclaration methodDecl) {
		this.handle(
			IProblem.IllegalModifierForAnnotationMethod,
			new String[] {
				new String(methodDecl.binding.declaringClass.readableName()),
				new String(methodDecl.selector),
			},
			new String[] {
				new String(methodDecl.binding.declaringClass.shortReadableName()),
				new String(methodDecl.selector),
			},
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void illegalModifierForAnnotationMemberType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForAnnotationMemberType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForAnnotationType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForAnnotationType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForClass(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForClass,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForEnum(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForEnum,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForEnumConstant(ReferenceBinding type, FieldDeclaration fieldDecl) {
		String[] arguments = new String[] {new String(fieldDecl.name)};
		this.handle(
			IProblem.IllegalModifierForEnumConstant,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	
	public void illegalModifierForEnumConstructor(AbstractMethodDeclaration constructor) {
		this.handle(
			IProblem.IllegalModifierForEnumConstructor,
			NoArgument,
			NoArgument,
			constructor.sourceStart,
			constructor.sourceEnd);
	}
	public void illegalModifierForField(ReferenceBinding type, FieldDeclaration fieldDecl) {
		String[] arguments = new String[] {new String(fieldDecl.name)};
		this.handle(
			IProblem.IllegalModifierForField,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void illegalModifierForInterface(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForInterface,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	
	public void illegalModifierForInterfaceField(FieldDeclaration fieldDecl) {
		String name = new String(fieldDecl.name);
		this.handle(
			IProblem.IllegalModifierForInterfaceField,
			new String[] {
				new String(fieldDecl.binding.declaringClass.readableName()),
				name,
			},
			new String[] {
				new String(fieldDecl.binding.declaringClass.shortReadableName()),
				name,
			},
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void illegalModifierForInterfaceMethod(AbstractMethodDeclaration methodDecl, boolean isJDK18orGreater) {
		// cannot include parameter types since they are not resolved yet
		// and the error message would be too long
		this.handle(
			isJDK18orGreater 
				? IProblem.IllegalModifierForInterfaceMethod18 
				: IProblem.IllegalModifierForInterfaceMethod,
			new String[] {
				new String(methodDecl.selector)
			},
			new String[] {
				new String(methodDecl.selector)
			},
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void illegalModifierForLocalClass(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForLocalClass,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForMemberClass(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForMemberClass,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForMemberEnum(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForMemberEnum,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForMemberInterface(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalModifierForMemberInterface,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalModifierForMethod(AbstractMethodDeclaration methodDecl) {
		// cannot include parameter types since they are not resolved yet
		// and the error message would be too long
		this.handle(
			methodDecl.isConstructor() ? IProblem.IllegalModifierForConstructor : IProblem.IllegalModifierForMethod,
			new String[] {
				new String(methodDecl.selector)
			},
			new String[] {
				new String(methodDecl.selector)
			},
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void illegalModifierForVariable(LocalDeclaration localDecl, boolean complainAsArgument) {
		String[] arguments = new String[] {new String(localDecl.name)};
		this.handle(
			complainAsArgument
				? IProblem.IllegalModifierForArgument
				: IProblem.IllegalModifierForVariable,
			arguments,
			arguments,
			localDecl.sourceStart,
			localDecl.sourceEnd);
	}
	public void illegalPrimitiveOrArrayTypeForEnclosingInstance(TypeBinding enclosingType, ASTNode location) {
		this.handle(
			IProblem.IllegalPrimitiveOrArrayTypeForEnclosingInstance,
			new String[] {new String(enclosingType.readableName())},
			new String[] {new String(enclosingType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void illegalQualifiedParameterizedTypeAllocation(TypeReference qualifiedTypeReference, TypeBinding allocatedType) {
		this.handle(
			IProblem.IllegalQualifiedParameterizedTypeAllocation,
			new String[] { new String(allocatedType.readableName()), new String(allocatedType.enclosingType().readableName()), },
			new String[] { new String(allocatedType.shortReadableName()), new String(allocatedType.enclosingType().shortReadableName()), },
			qualifiedTypeReference.sourceStart,
			qualifiedTypeReference.sourceEnd);
	}
	public void illegalStaticModifierForMemberType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalStaticModifierForMemberType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalUsageOfQualifiedTypeReference(QualifiedTypeReference qualifiedTypeReference) {
		StringBuffer buffer = new StringBuffer();
		char[][] tokens = qualifiedTypeReference.tokens;
		for (int i = 0; i < tokens.length; i++) {
			if (i > 0) buffer.append('.');
			buffer.append(tokens[i]);
		}
		String[] arguments = new String[] { String.valueOf(buffer)};
		this.handle(
			IProblem.IllegalUsageOfQualifiedTypeReference,
			arguments,
			arguments,
			qualifiedTypeReference.sourceStart,
			qualifiedTypeReference.sourceEnd);
	}
	public void illegalUsageOfWildcard(TypeReference wildcard) {
		this.handle(
			IProblem.InvalidUsageOfWildcard,
			NoArgument,
			NoArgument,
			wildcard.sourceStart,
			wildcard.sourceEnd);
	}
	public void illegalVararg(Argument argType, AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {CharOperation.toString(argType.type.getTypeName()), new String(methodDecl.selector)};
		this.handle(
			IProblem.IllegalVararg,
			arguments,
			arguments,
			argType.sourceStart,
			argType.sourceEnd);
	}
	public void illegalVarargInLambda(Argument argType) {
		String[] arguments = new String[] { CharOperation.toString(argType.type.getTypeName())};
		this.handle(
			IProblem.IllegalVarargInLambda,
			arguments,
			arguments,
			argType.sourceStart,
			argType.sourceEnd);
	}
	public void illegalThisDeclaration(Argument argument) {
		String[] arguments = NoArgument;
		this.handle(
			IProblem.IllegalDeclarationOfThisParameter,
			arguments,
			arguments,
			argument.sourceStart,
			argument.sourceEnd);
	}
	public void illegalSourceLevelForThis(Argument argument) {
		String[] arguments = NoArgument;
		this.handle(
			IProblem.ExplicitThisParameterNotBelow18,
			arguments,
			arguments,
			argument.sourceStart,
			argument.sourceEnd);
	}
	public void disallowedThisParameter(Receiver receiver) {
		String[] arguments = NoArgument;
		this.handle(
			IProblem.DisallowedExplicitThisParameter,
			arguments,
			arguments,
			receiver.sourceStart,
			receiver.sourceEnd);
	}
	public void illegalQualifierForExplicitThis(Receiver receiver, TypeBinding expectedType) {
		String[] problemArguments = new String[] { new String(expectedType.sourceName())};
		this.handle(
			IProblem.IllegalQualifierForExplicitThis,
			problemArguments,
			problemArguments,
			(receiver.qualifyingName == null) ? receiver.sourceStart : receiver.qualifyingName.sourceStart,
			receiver.sourceEnd);
	}
	public void illegalQualifierForExplicitThis2(Receiver receiver) {
		this.handle(
			IProblem.IllegalQualifierForExplicitThis2,
			NoArgument,
			NoArgument,
			receiver.qualifyingName.sourceStart,
			receiver.sourceEnd);
	}
	public void illegalTypeForExplicitThis(Receiver receiver, TypeBinding expectedType) {
		this.handle(
			IProblem.IllegalTypeForExplicitThis,
			new String[] { new String(expectedType.readableName())},
			new String[] { new String(expectedType.shortReadableName())},
			receiver.type.sourceStart,
			receiver.type.sourceEnd);
	}
	public void illegalThis(Argument argument) {
		String[] arguments = NoArgument;
		this.handle(
			IProblem.ExplicitThisParameterNotInLambda,
			arguments,
			arguments,
			argument.sourceStart,
			argument.sourceEnd);
	}
	public void defaultMethodsNotBelow18(MethodDeclaration md) {
		this.handle(
				IProblem.DefaultMethodNotBelow18,
				NoArgument,
				NoArgument,
				md.sourceStart,
				md.sourceEnd);
	}
	public void staticInterfaceMethodsNotBelow18(MethodDeclaration md) {
		this.handle(
				IProblem.StaticInterfaceMethodNotBelow18,
				NoArgument,
				NoArgument,
				md.sourceStart,
				md.sourceEnd);
	}
	public void referenceExpressionsNotBelow18(ReferenceExpression rexp) {
		this.handle(
				rexp.isMethodReference() ? IProblem.MethodReferenceNotBelow18 : IProblem.ConstructorReferenceNotBelow18,
				NoArgument,
				NoArgument,
				rexp.sourceStart,
				rexp.sourceEnd);
	}
	public void lambdaExpressionsNotBelow18(LambdaExpression lexp) {
		this.handle(
				IProblem.LambdaExpressionNotBelow18,
				NoArgument,
				NoArgument,
				lexp.sourceStart,
				lexp.diagnosticsSourceEnd());
	}
	public void illegalVisibilityModifierCombinationForField(ReferenceBinding type, FieldDeclaration fieldDecl) {
		String[] arguments = new String[] {new String(fieldDecl.name)};
		this.handle(
			IProblem.IllegalVisibilityModifierCombinationForField,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void illegalVisibilityModifierCombinationForMemberType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalVisibilityModifierCombinationForMemberType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalVisibilityModifierCombinationForMethod(ReferenceBinding type, AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {new String(type.sourceName()), new String(methodDecl.selector)};
		this.handle(
			IProblem.IllegalVisibilityModifierCombinationForMethod,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void illegalVisibilityModifierForInterfaceMemberType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.IllegalVisibilityModifierForInterfaceMemberType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void illegalVoidExpression(ASTNode location) {
		this.handle(
			IProblem.InvalidVoidExpression,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void importProblem(ImportReference importRef, Binding expectedImport) {
		if (expectedImport instanceof FieldBinding) {
			int id = IProblem.UndefinedField;
			FieldBinding field = (FieldBinding) expectedImport;
			String[] readableArguments = null;
			String[] shortArguments = null;
			switch (expectedImport.problemId()) {
				case ProblemReasons.NotVisible :
					id = IProblem.NotVisibleField;
					readableArguments = new String[] {CharOperation.toString(importRef.tokens), new String(field.declaringClass.readableName())};
					shortArguments = new String[] {CharOperation.toString(importRef.tokens), new String(field.declaringClass.shortReadableName())};
					break;
				case ProblemReasons.Ambiguous :
					id = IProblem.AmbiguousField;
					readableArguments = new String[] {new String(field.readableName())};
					shortArguments = new String[] {new String(field.readableName())};
					break;
				case ProblemReasons.ReceiverTypeNotVisible :
					id = IProblem.NotVisibleType;
					readableArguments = new String[] {new String(field.declaringClass.leafComponentType().readableName())};
					shortArguments = new String[] {new String(field.declaringClass.leafComponentType().shortReadableName())};
					break;
			}
			this.handle(
				id,
				readableArguments,
				shortArguments,
				nodeSourceStart(field, importRef),
				nodeSourceEnd(field, importRef));
			return;
		}
	
		if (expectedImport.problemId() == ProblemReasons.NotFound) {
			char[][] tokens = expectedImport instanceof ProblemReferenceBinding
				? ((ProblemReferenceBinding) expectedImport).compoundName
				: importRef.tokens;
			String[] arguments = new String[]{CharOperation.toString(tokens)};
			this.handle(
			        IProblem.ImportNotFound,
			        arguments,
			        arguments,
			        importRef.sourceStart,
			        (int) importRef.sourcePositions[tokens.length - 1]);
			return;
		}
		if (expectedImport.problemId() == ProblemReasons.InvalidTypeForStaticImport) {
			char[][] tokens = importRef.tokens;
			String[] arguments = new String[]{CharOperation.toString(tokens)};
			this.handle(
			        IProblem.InvalidTypeForStaticImport,
			        arguments,
			        arguments,
			        importRef.sourceStart,
			        (int) importRef.sourcePositions[tokens.length - 1]);
			return;
		}
		invalidType(importRef, (TypeBinding)expectedImport);
	}
	public void incompatibleExceptionInThrowsClause(SourceTypeBinding type, MethodBinding currentMethod, MethodBinding inheritedMethod, ReferenceBinding exceptionType) {
		if (TypeBinding.equalsEquals(type, currentMethod.declaringClass)) {
			int id;
			if (currentMethod.declaringClass.isInterface()
					&& !inheritedMethod.isPublic()){ // interface inheriting Object protected method
				id = IProblem.IncompatibleExceptionInThrowsClauseForNonInheritedInterfaceMethod;
			} else {
				id = IProblem.IncompatibleExceptionInThrowsClause;
			}
			this.handle(
				// Exception %1 is not compatible with throws clause in %2
				// 9.4.4 - The type of exception in the throws clause is incompatible.
				id,
				new String[] {
					new String(exceptionType.sourceName()),
					new String(
						CharOperation.concat(
							inheritedMethod.declaringClass.readableName(),
							inheritedMethod.readableName(),
							'.'))},
				new String[] {
					new String(exceptionType.sourceName()),
					new String(
						CharOperation.concat(
							inheritedMethod.declaringClass.shortReadableName(),
							inheritedMethod.shortReadableName(),
							'.'))},
				currentMethod.sourceStart(),
				currentMethod.sourceEnd());
		} else
			this.handle(
				// Exception %1 in throws clause of %2 is not compatible with %3
				// 9.4.4 - The type of exception in the throws clause is incompatible.
				IProblem.IncompatibleExceptionInInheritedMethodThrowsClause,
				new String[] {
					new String(exceptionType.sourceName()),
					new String(
						CharOperation.concat(
							currentMethod.declaringClass.sourceName(),
							currentMethod.readableName(),
							'.')),
					new String(
						CharOperation.concat(
							inheritedMethod.declaringClass.readableName(),
							inheritedMethod.readableName(),
							'.'))},
				new String[] {
					new String(exceptionType.sourceName()),
					new String(
						CharOperation.concat(
							currentMethod.declaringClass.sourceName(),
							currentMethod.shortReadableName(),
							'.')),
					new String(
						CharOperation.concat(
							inheritedMethod.declaringClass.shortReadableName(),
							inheritedMethod.shortReadableName(),
							'.'))},
				type.sourceStart(),
				type.sourceEnd());
	}
	public void incompatibleReturnType(MethodBinding currentMethod, MethodBinding inheritedMethod) {
		StringBuffer methodSignature = new StringBuffer();
		methodSignature
			.append(inheritedMethod.declaringClass.readableName())
			.append('.')
			.append(inheritedMethod.readableName());
	
		StringBuffer shortSignature = new StringBuffer();
		shortSignature
			.append(inheritedMethod.declaringClass.shortReadableName())
			.append('.')
			.append(inheritedMethod.shortReadableName());
	
		int id;
		final ReferenceBinding declaringClass = currentMethod.declaringClass;
		if (declaringClass.isInterface()
				&& !inheritedMethod.isPublic()){ // interface inheriting Object protected method
			id = IProblem.IncompatibleReturnTypeForNonInheritedInterfaceMethod;
		} else {
			id = IProblem.IncompatibleReturnType;
		}
		AbstractMethodDeclaration method = currentMethod.sourceMethod();
		int sourceStart = 0;
		int sourceEnd = 0;
		if (method == null) {
			if (declaringClass instanceof SourceTypeBinding) {
				SourceTypeBinding sourceTypeBinding = (SourceTypeBinding) declaringClass;
				sourceStart = sourceTypeBinding.sourceStart();
				sourceEnd = sourceTypeBinding.sourceEnd();
			}
		} else if (method.isConstructor()){
			sourceStart = method.sourceStart;
			sourceEnd = method.sourceEnd;
		} else {
			TypeReference returnType = ((MethodDeclaration) method).returnType;
			sourceStart = returnType.sourceStart;
			if (returnType instanceof ParameterizedSingleTypeReference) {
				ParameterizedSingleTypeReference typeReference = (ParameterizedSingleTypeReference) returnType;
				TypeReference[] typeArguments = typeReference.typeArguments;
				if (typeArguments[typeArguments.length - 1].sourceEnd > typeReference.sourceEnd) {
					sourceEnd = retrieveClosingAngleBracketPosition(typeReference.sourceEnd);
				} else {
					sourceEnd = returnType.sourceEnd;
				}
			} else if (returnType instanceof ParameterizedQualifiedTypeReference) {
				ParameterizedQualifiedTypeReference typeReference = (ParameterizedQualifiedTypeReference) returnType;
				sourceEnd = retrieveClosingAngleBracketPosition(typeReference.sourceEnd);
			} else {
				sourceEnd = returnType.sourceEnd;
			}
		}
		this.handle(
			id,
			new String[] {methodSignature.toString()},
			new String[] {shortSignature.toString()},
			sourceStart,
			sourceEnd);
	}
	public void incorrectArityForParameterizedType(ASTNode location, TypeBinding type, TypeBinding[] argumentTypes) {
		incorrectArityForParameterizedType(location, type, argumentTypes, Integer.MAX_VALUE);
	}
	public void incorrectArityForParameterizedType(ASTNode location, TypeBinding type, TypeBinding[] argumentTypes, int index) {
	    if (location == null) {
			this.handle(
				IProblem.IncorrectArityForParameterizedType,
				new String[] {new String(type.readableName()), typesAsString(argumentTypes, false)},
				new String[] {new String(type.shortReadableName()), typesAsString(argumentTypes, true)},
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
			return; // not reached since aborted above
	    }
		this.handle(
			IProblem.IncorrectArityForParameterizedType,
			new String[] {new String(type.readableName()), typesAsString(argumentTypes, false)},
			new String[] {new String(type.shortReadableName()), typesAsString(argumentTypes, true)},
			location.sourceStart,
			nodeSourceEnd(null, location, index));
	}
	public void diamondNotBelow17(ASTNode location) {
		diamondNotBelow17(location, Integer.MAX_VALUE);
	}
	public void diamondNotBelow17(ASTNode location, int index) {
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=348493
	    if (location == null) {
			this.handle(
				IProblem.DiamondNotBelow17,
				NoArgument,
				NoArgument,
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
			return; // not reached since aborted above
	    }
		this.handle(
			IProblem.DiamondNotBelow17,
			NoArgument,
			NoArgument,
			location.sourceStart,
			nodeSourceEnd(null, location, index));
	}
	public void incorrectLocationForNonEmptyDimension(ArrayAllocationExpression expression, int index) {
		this.handle(
			IProblem.IllegalDimension,
			NoArgument,
			NoArgument,
			expression.dimensions[index].sourceStart,
			expression.dimensions[index].sourceEnd);
	}
	public void incorrectSwitchType(Expression expression, TypeBinding testType) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_7) {
			if (testType.id == TypeIds.T_JavaLangString) {
				this.handle(
						IProblem.SwitchOnStringsNotBelow17,
						new String[] {new String(testType.readableName())},
						new String[] {new String(testType.shortReadableName())},
						expression.sourceStart,
						expression.sourceEnd);
			} else {
				if (this.options.sourceLevel < ClassFileConstants.JDK1_5 && testType.isEnum()) {
					this.handle(
							IProblem.SwitchOnEnumNotBelow15,
							new String[] {new String(testType.readableName())},
							new String[] {new String(testType.shortReadableName())},
							expression.sourceStart,
							expression.sourceEnd);
				} else {
					this.handle(
							IProblem.IncorrectSwitchType,
							new String[] {new String(testType.readableName())},
							new String[] {new String(testType.shortReadableName())},
							expression.sourceStart,
							expression.sourceEnd);
				}
			}
		} else {
			this.handle(
					IProblem.IncorrectSwitchType17,
					new String[] {new String(testType.readableName())},
					new String[] {new String(testType.shortReadableName())},
					expression.sourceStart,
					expression.sourceEnd);
		}
	}
	public void indirectAccessToStaticField(ASTNode location, FieldBinding field){
		int severity = computeSeverity(IProblem.IndirectAccessToStaticField);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.IndirectAccessToStaticField,
			new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
			new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
			severity,
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	public void indirectAccessToStaticMethod(ASTNode location, MethodBinding method) {
		int severity = computeSeverity(IProblem.IndirectAccessToStaticMethod);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.IndirectAccessToStaticMethod,
			new String[] {new String(method.declaringClass.readableName()), new String(method.selector), typesAsString(method, false)},
			new String[] {new String(method.declaringClass.shortReadableName()), new String(method.selector), typesAsString(method, true)},
			severity,
			location.sourceStart,
			location.sourceEnd);
	}
	public void inheritedDefaultMethodConflictsWithOtherInherited(SourceTypeBinding type, MethodBinding defaultMethod, MethodBinding otherMethod) {
		TypeDeclaration typeDecl = type.scope.referenceContext;
		String[] problemArguments = new String[] { 
				String.valueOf(defaultMethod.readableName()), 
				String.valueOf(defaultMethod.declaringClass.readableName()), 
				String.valueOf(otherMethod.declaringClass.readableName()) };
		String[] messageArguments = new String[] { 
				String.valueOf(defaultMethod.shortReadableName()), 
				String.valueOf(defaultMethod.declaringClass.shortReadableName()), 
				String.valueOf(otherMethod.declaringClass.shortReadableName()) };	
		this.handle(IProblem.InheritedDefaultMethodConflictsWithOtherInherited,
				problemArguments,
				messageArguments,
				typeDecl.sourceStart,
				typeDecl.sourceEnd);
	}
	private void inheritedMethodReducesVisibility(int sourceStart, int sourceEnd, MethodBinding concreteMethod, MethodBinding[] abstractMethods) {
		StringBuffer concreteSignature = new StringBuffer();
		concreteSignature
			.append(concreteMethod.declaringClass.readableName())
			.append('.')
			.append(concreteMethod.readableName());
		StringBuffer shortSignature = new StringBuffer();
		shortSignature
			.append(concreteMethod.declaringClass.shortReadableName())
			.append('.')
			.append(concreteMethod.shortReadableName());
		this.handle(
			// The inherited method %1 cannot hide the public abstract method in %2
			IProblem.InheritedMethodReducesVisibility,
			new String[] {
				concreteSignature.toString(),
				new String(abstractMethods[0].declaringClass.readableName())},
			new String[] {
				shortSignature.toString(),
				new String(abstractMethods[0].declaringClass.shortReadableName())},
			sourceStart,
			sourceEnd);
	}
	public void inheritedMethodReducesVisibility(SourceTypeBinding type, MethodBinding concreteMethod, MethodBinding[] abstractMethods) {
		inheritedMethodReducesVisibility(type.sourceStart(), type.sourceEnd(), concreteMethod, abstractMethods);
	}
	public void inheritedMethodReducesVisibility(TypeParameter typeParameter, MethodBinding concreteMethod, MethodBinding[] abstractMethods) {
		inheritedMethodReducesVisibility(typeParameter.sourceStart(), typeParameter.sourceEnd(), concreteMethod, abstractMethods);
	}
	public void inheritedMethodsHaveIncompatibleReturnTypes(ASTNode location, MethodBinding[] inheritedMethods, int length) {
		StringBuffer methodSignatures = new StringBuffer();
		StringBuffer shortSignatures = new StringBuffer();
		for (int i = length; --i >= 0;) {
			methodSignatures
				.append(inheritedMethods[i].declaringClass.readableName())
				.append('.')
				.append(inheritedMethods[i].readableName());
			shortSignatures
				.append(inheritedMethods[i].declaringClass.shortReadableName())
				.append('.')
				.append(inheritedMethods[i].shortReadableName());
			if (i != 0){
				methodSignatures.append(", "); //$NON-NLS-1$
				shortSignatures.append(", "); //$NON-NLS-1$
			}
		}
	
		this.handle(
			// Return type is incompatible with %1
			// 9.4.2 - The return type from the method is incompatible with the declaration.
			IProblem.InheritedIncompatibleReturnType,
			new String[] {methodSignatures.toString()},
			new String[] {shortSignatures.toString()},
			location.sourceStart,
			location.sourceEnd);
	}
	public void inheritedMethodsHaveIncompatibleReturnTypes(SourceTypeBinding type, MethodBinding[] inheritedMethods, int length, boolean[] isOverridden) {
		StringBuffer methodSignatures = new StringBuffer();
		StringBuffer shortSignatures = new StringBuffer();
		for (int i = length; --i >= 0;) {
			if (isOverridden[i]) continue;
			methodSignatures
				.append(inheritedMethods[i].declaringClass.readableName())
				.append('.')
				.append(inheritedMethods[i].readableName());
			shortSignatures
				.append(inheritedMethods[i].declaringClass.shortReadableName())
				.append('.')
				.append(inheritedMethods[i].shortReadableName());
			if (i != 0){
				methodSignatures.append(", "); //$NON-NLS-1$
				shortSignatures.append(", "); //$NON-NLS-1$
			}
		}
	
		this.handle(
			// Return type is incompatible with %1
			// 9.4.2 - The return type from the method is incompatible with the declaration.
			IProblem.InheritedIncompatibleReturnType,
			new String[] {methodSignatures.toString()},
			new String[] {shortSignatures.toString()},
			type.sourceStart(),
			type.sourceEnd());
	}
	public void inheritedMethodsHaveNameClash(SourceTypeBinding type, MethodBinding oneMethod, MethodBinding twoMethod) {
		this.handle(
			IProblem.MethodNameClash,
			new String[] {
				new String(oneMethod.selector),
				typesAsString(oneMethod.original(), false),
				new String(oneMethod.declaringClass.readableName()),
				typesAsString(twoMethod.original(), false),
				new String(twoMethod.declaringClass.readableName()),
			 },
			new String[] {
				new String(oneMethod.selector),
				typesAsString(oneMethod.original(), true),
				new String(oneMethod.declaringClass.shortReadableName()),
				typesAsString(twoMethod.original(), true),
				new String(twoMethod.declaringClass.shortReadableName()),
			 },
			 type.sourceStart(),
			 type.sourceEnd());
	}
	public void initializerMustCompleteNormally(FieldDeclaration fieldDecl) {
		this.handle(
			IProblem.InitializerMustCompleteNormally,
			NoArgument,
			NoArgument,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void innerTypesCannotDeclareStaticInitializers(ReferenceBinding innerType, Initializer initializer) {
		this.handle(
			IProblem.CannotDefineStaticInitializerInLocalType,
			new String[] {new String(innerType.readableName())},
			new String[] {new String(innerType.shortReadableName())},
			initializer.sourceStart,
			initializer.sourceStart);
	}
	public void interfaceCannotHaveConstructors(ConstructorDeclaration constructor) {
		this.handle(
			IProblem.InterfaceCannotHaveConstructors,
			NoArgument,
			NoArgument,
			constructor.sourceStart,
			constructor.sourceEnd,
			constructor,
			constructor.compilationResult());
	}
	public void interfaceCannotHaveInitializers(char [] sourceName, FieldDeclaration fieldDecl) {
		String[] arguments = new String[] {new String(sourceName)};
	
		this.handle(
			IProblem.InterfaceCannotHaveInitializers,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void invalidAnnotationMemberType(MethodDeclaration methodDecl) {
		this.handle(
			IProblem.InvalidAnnotationMemberType,
			new String[] {
				new String(methodDecl.binding.returnType.readableName()),
				new String(methodDecl.selector),
				new String(methodDecl.binding.declaringClass.readableName()),
			},
			new String[] {
				new String(methodDecl.binding.returnType.shortReadableName()),
				new String(methodDecl.selector),
				new String(methodDecl.binding.declaringClass.shortReadableName()),
			},
			methodDecl.returnType.sourceStart,
			methodDecl.returnType.sourceEnd);
	
	}
	public void invalidBreak(ASTNode location) {
		this.handle(
			IProblem.InvalidBreak,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void invalidConstructor(Statement statement, MethodBinding targetConstructor) {
		boolean insideDefaultConstructor =
			(this.referenceContext instanceof ConstructorDeclaration)
				&& ((ConstructorDeclaration)this.referenceContext).isDefaultConstructor();
		boolean insideImplicitConstructorCall =
			(statement instanceof ExplicitConstructorCall)
				&& (((ExplicitConstructorCall) statement).accessMode == ExplicitConstructorCall.ImplicitSuper);
	
		int sourceStart = statement.sourceStart;
		int sourceEnd = statement.sourceEnd;
		if (statement instanceof AllocationExpression) {
			AllocationExpression allocation = (AllocationExpression)statement;
			if (allocation.enumConstant != null) {
				sourceStart = allocation.enumConstant.sourceStart;
				sourceEnd = allocation.enumConstant.sourceEnd;
			}
		}
	
		int id = IProblem.UndefinedConstructor; //default...
	    MethodBinding shownConstructor = targetConstructor;
		switch (targetConstructor.problemId()) {
			case ProblemReasons.NotFound :
				ProblemMethodBinding problemConstructor = (ProblemMethodBinding) targetConstructor;
				if (problemConstructor.closestMatch != null) {
			    	if ((problemConstructor.closestMatch.tagBits & TagBits.HasMissingType) != 0) {
						missingTypeInConstructor(statement, problemConstructor.closestMatch);
						return;
			    	}
			    }
	
				if (insideDefaultConstructor){
					id = IProblem.UndefinedConstructorInDefaultConstructor;
				} else if (insideImplicitConstructorCall){
					id = IProblem.UndefinedConstructorInImplicitConstructorCall;
				} else {
					id = IProblem.UndefinedConstructor;
				}
				break;
			case ProblemReasons.NotVisible :
				if (insideDefaultConstructor){
					id = IProblem.NotVisibleConstructorInDefaultConstructor;
				} else if (insideImplicitConstructorCall){
					id = IProblem.NotVisibleConstructorInImplicitConstructorCall;
				} else {
					id = IProblem.NotVisibleConstructor;
				}
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				if (problemConstructor.closestMatch != null) {
				    shownConstructor = problemConstructor.closestMatch.original();
			    }
				break;
			case ProblemReasons.Ambiguous :
				if (insideDefaultConstructor){
					id = IProblem.AmbiguousConstructorInDefaultConstructor;
				} else if (insideImplicitConstructorCall){
					id = IProblem.AmbiguousConstructorInImplicitConstructorCall;
				} else {
					id = IProblem.AmbiguousConstructor;
				}
				break;
			case ProblemReasons.ParameterBoundMismatch :
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				ParameterizedGenericMethodBinding substitutedConstructor = (ParameterizedGenericMethodBinding) problemConstructor.closestMatch;
				shownConstructor = substitutedConstructor.original();
				int augmentedLength = problemConstructor.parameters.length;
				TypeBinding inferredTypeArgument = problemConstructor.parameters[augmentedLength-2];
				TypeVariableBinding typeParameter = (TypeVariableBinding) problemConstructor.parameters[augmentedLength-1];
				TypeBinding[] invocationArguments = new TypeBinding[augmentedLength-2]; // remove extra info from the end
				System.arraycopy(problemConstructor.parameters, 0, invocationArguments, 0, augmentedLength-2);
				this.handle(
					IProblem.GenericConstructorTypeArgumentMismatch,
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, false),
					        new String(shownConstructor.declaringClass.readableName()),
					        typesAsString(invocationArguments, false),
					        new String(inferredTypeArgument.readableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, false) },
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, true),
					        new String(shownConstructor.declaringClass.shortReadableName()),
					        typesAsString(invocationArguments, true),
					        new String(inferredTypeArgument.shortReadableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, true) },
					sourceStart,
					sourceEnd);
				return;
	
			case ProblemReasons.TypeParameterArityMismatch :
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				if (shownConstructor.typeVariables == Binding.NO_TYPE_VARIABLES) {
					this.handle(
						IProblem.NonGenericConstructor,
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, false),
						        new String(shownConstructor.declaringClass.readableName()),
						        typesAsString(targetConstructor, false) },
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, true),
						        new String(shownConstructor.declaringClass.shortReadableName()),
						        typesAsString(targetConstructor, true) },
						sourceStart,
						sourceEnd);
				} else {
					this.handle(
						IProblem.IncorrectArityForParameterizedConstructor  ,
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, false),
						        new String(shownConstructor.declaringClass.readableName()),
								typesAsString(shownConstructor.typeVariables, false),
						        typesAsString(targetConstructor, false) },
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, true),
						        new String(shownConstructor.declaringClass.shortReadableName()),
								typesAsString(shownConstructor.typeVariables, true),
						        typesAsString(targetConstructor, true) },
						sourceStart,
						sourceEnd);
				}
				return;
			case ProblemReasons.ParameterizedMethodTypeMismatch :
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				this.handle(
					IProblem.ParameterizedConstructorArgumentTypeMismatch,
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, false),
					        new String(shownConstructor.declaringClass.readableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownConstructor).typeArguments, false),
					        typesAsString(targetConstructor, false) },
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, true),
					        new String(shownConstructor.declaringClass.shortReadableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownConstructor).typeArguments, true),
					        typesAsString(targetConstructor, true) },
					sourceStart,
					sourceEnd);
				return;
			case ProblemReasons.TypeArgumentsForRawGenericMethod :
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				this.handle(
					IProblem.TypeArgumentsForRawGenericConstructor,
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, false),
					        new String(shownConstructor.declaringClass.readableName()),
					        typesAsString(targetConstructor, false) },
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, true),
					        new String(shownConstructor.declaringClass.shortReadableName()),
					        typesAsString(targetConstructor, true) },
					sourceStart,
					sourceEnd);
				return;
			case ProblemReasons.VarargsElementTypeNotVisible :
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				TypeBinding varargsElementType = shownConstructor.parameters[shownConstructor.parameters.length - 1].leafComponentType();
				this.handle(
					IProblem.VarargsElementTypeNotVisibleForConstructor,
					new String[] {
							new String(shownConstructor.declaringClass.sourceName()),
							typesAsString(shownConstructor, false),
							new String(shownConstructor.declaringClass.readableName()),
							new String(varargsElementType.readableName())
					},
					new String[] {
							new String(shownConstructor.declaringClass.sourceName()),
							typesAsString(shownConstructor, true),
							new String(shownConstructor.declaringClass.shortReadableName()),
							new String(varargsElementType.shortReadableName())
					},
					sourceStart,
					sourceEnd);
				return;
			case ProblemReasons.ParameterizedMethodExpectedTypeProblem:
				// FIXME(stephan): construct suitable message (https://bugs.eclipse.org/404675)
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				this.handle(
					IProblem.TypeMismatch,
					new String[] {
					        String.valueOf(shownConstructor.returnType.readableName()),
					        (problemConstructor.returnType != null ? String.valueOf(problemConstructor.returnType.readableName()) : "<unknown>")}, //$NON-NLS-1$
					new String[] {
					        String.valueOf(shownConstructor.returnType.shortReadableName()),
					        (problemConstructor.returnType != null ? String.valueOf(problemConstructor.returnType.shortReadableName()) : "<unknown>")}, //$NON-NLS-1$
					statement.sourceStart,
					statement.sourceEnd);
				return;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(statement); // want to fail to see why we were here...
				break;
		}
	
		this.handle(
			id,
			new String[] {new String(targetConstructor.declaringClass.readableName()), typesAsString(shownConstructor, false)},
			new String[] {new String(targetConstructor.declaringClass.shortReadableName()), typesAsString(shownConstructor, true)},
			sourceStart,
			sourceEnd);
	}
	public void invalidContinue(ASTNode location) {
		this.handle(
			IProblem.InvalidContinue,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void invalidEnclosingType(Expression expression, TypeBinding type, ReferenceBinding enclosingType) {
	
		if (enclosingType.isAnonymousType()) enclosingType = enclosingType.superclass();
		if (enclosingType.sourceName != null && enclosingType.sourceName.length == 0) return;
	
		int flag = IProblem.UndefinedType; // default
		switch (type.problemId()) {
			case ProblemReasons.NotFound : // 1
				flag = IProblem.UndefinedType;
				break;
			case ProblemReasons.NotVisible : // 2
				flag = IProblem.NotVisibleType;
				break;
			case ProblemReasons.Ambiguous : // 3
				flag = IProblem.AmbiguousType;
				break;
			case ProblemReasons.InternalNameProvided :
				flag = IProblem.InternalTypeNameProvided;
				break;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(expression); // want to fail to see why we were here...
				break;
		}
	
		this.handle(
			flag,
			new String[] {new String(enclosingType.readableName()) + "." + new String(type.readableName())}, //$NON-NLS-1$
			new String[] {new String(enclosingType.shortReadableName()) + "." + new String(type.shortReadableName())}, //$NON-NLS-1$
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidExplicitConstructorCall(ASTNode location) {
	
		this.handle(
			IProblem.InvalidExplicitConstructorCall,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void invalidExpressionAsStatement(Expression expression){
		this.handle(
			IProblem.InvalidExpressionAsStatement,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidField(FieldReference fieldRef, TypeBinding searchedType) {
		if(isRecoveredName(fieldRef.token)) return;
	
		int id = IProblem.UndefinedField;
		FieldBinding field = fieldRef.binding;
		switch (field.problemId()) {
			case ProblemReasons.NotFound :
				if ((searchedType.tagBits & TagBits.HasMissingType) != 0) {
					this.handle(
							IProblem.UndefinedType,
							new String[] {new String(searchedType.leafComponentType().readableName())},
							new String[] {new String(searchedType.leafComponentType().shortReadableName())},
							fieldRef.receiver.sourceStart,
							fieldRef.receiver.sourceEnd);
						return;
				}
				id = IProblem.UndefinedField;
	/* also need to check that the searchedType is the receiver type
				if (searchedType.isHierarchyInconsistent())
					severity = SecondaryError;
	*/
				break;
			case ProblemReasons.NotVisible :
				this.handle(
					IProblem.NotVisibleField,
					new String[] {new String(fieldRef.token), new String(field.declaringClass.readableName())},
					new String[] {new String(fieldRef.token), new String(field.declaringClass.shortReadableName())},
					nodeSourceStart(field, fieldRef),
					nodeSourceEnd(field, fieldRef));
				return;
			case ProblemReasons.Ambiguous :
				id = IProblem.AmbiguousField;
				break;
			case ProblemReasons.NonStaticReferenceInStaticContext :
				id = IProblem.NonStaticFieldFromStaticInvocation;
				break;
			case ProblemReasons.NonStaticReferenceInConstructorInvocation :
				id = IProblem.InstanceFieldDuringConstructorInvocation;
				break;
			case ProblemReasons.InheritedNameHidesEnclosingName :
				id = IProblem.InheritedFieldHidesEnclosingName;
				break;
			case ProblemReasons.ReceiverTypeNotVisible :
				this.handle(
					IProblem.NotVisibleType, // cannot occur in javadoc comments
					new String[] {new String(searchedType.leafComponentType().readableName())},
					new String[] {new String(searchedType.leafComponentType().shortReadableName())},
					fieldRef.receiver.sourceStart,
					fieldRef.receiver.sourceEnd);
				return;
	
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(fieldRef); // want to fail to see why we were here...
				break;
		}
	
		String[] arguments = new String[] {new String(field.readableName())};
		this.handle(
			id,
			arguments,
			arguments,
			nodeSourceStart(field, fieldRef),
			nodeSourceEnd(field, fieldRef));
	}
	public void invalidField(NameReference nameRef, FieldBinding field) {
		if (nameRef instanceof QualifiedNameReference) {
			QualifiedNameReference ref = (QualifiedNameReference) nameRef;
			if (isRecoveredName(ref.tokens)) return;
		} else {
			SingleNameReference ref = (SingleNameReference) nameRef;
			if (isRecoveredName(ref.token)) return;
		}
		int id = IProblem.UndefinedField;
		switch (field.problemId()) {
			case ProblemReasons.NotFound :
				TypeBinding declaringClass = field.declaringClass;
				if (declaringClass != null && (declaringClass.tagBits & TagBits.HasMissingType) != 0) {
					this.handle(
							IProblem.UndefinedType,
							new String[] {new String(field.declaringClass.readableName())},
							new String[] {new String(field.declaringClass.shortReadableName())},
							nameRef.sourceStart,
							nameRef.sourceEnd);
						return;
				}
				String[] arguments = new String[] {new String(field.readableName())};
				this.handle(
						id,
						arguments,
						arguments,
						nodeSourceStart(field, nameRef),
						nodeSourceEnd(field, nameRef));
				return;
			case ProblemReasons.NotVisible :
				char[] name = field.readableName();
				name = CharOperation.lastSegment(name, '.');
				this.handle(
					IProblem.NotVisibleField,
					new String[] {new String(name), new String(field.declaringClass.readableName())},
					new String[] {new String(name), new String(field.declaringClass.shortReadableName())},
					nodeSourceStart(field, nameRef),
					nodeSourceEnd(field, nameRef));
				return;
			case ProblemReasons.Ambiguous :
				id = IProblem.AmbiguousField;
				break;
			case ProblemReasons.NonStaticReferenceInStaticContext :
				id = IProblem.NonStaticFieldFromStaticInvocation;
				break;
			case ProblemReasons.NonStaticReferenceInConstructorInvocation :
				id = IProblem.InstanceFieldDuringConstructorInvocation;
				break;
			case ProblemReasons.InheritedNameHidesEnclosingName :
				id = IProblem.InheritedFieldHidesEnclosingName;
				break;
			case ProblemReasons.ReceiverTypeNotVisible :
				this.handle(
					IProblem.NotVisibleType,
					new String[] {new String(field.declaringClass.readableName())},
					new String[] {new String(field.declaringClass.shortReadableName())},
					nameRef.sourceStart,
					nameRef.sourceEnd);
				return;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(nameRef); // want to fail to see why we were here...
				break;
		}
		String[] arguments = new String[] {new String(field.readableName())};
		this.handle(
			id,
			arguments,
			arguments,
			nameRef.sourceStart,
			nameRef.sourceEnd);
	}
	public void invalidField(QualifiedNameReference nameRef, FieldBinding field, int index, TypeBinding searchedType) {
		//the resolution of the index-th field of qname failed
		//qname.otherBindings[index] is the binding that has produced the error
	
		//The different targetted errors should be :
		//UndefinedField
		//NotVisibleField
		//AmbiguousField
	
		if (isRecoveredName(nameRef.tokens)) return;
	
		if (searchedType.isBaseType()) {
			this.handle(
				IProblem.NoFieldOnBaseType,
				new String[] {
					new String(searchedType.readableName()),
					CharOperation.toString(CharOperation.subarray(nameRef.tokens, 0, index)),
					new String(nameRef.tokens[index])},
				new String[] {
					new String(searchedType.sourceName()),
					CharOperation.toString(CharOperation.subarray(nameRef.tokens, 0, index)),
					new String(nameRef.tokens[index])},
				nameRef.sourceStart,
				(int) nameRef.sourcePositions[index]);
			return;
		}
	
		int id = IProblem.UndefinedField;
		switch (field.problemId()) {
			case ProblemReasons.NotFound :
				if ((searchedType.tagBits & TagBits.HasMissingType) != 0) {
					this.handle(
							IProblem.UndefinedType,
							new String[] {new String(searchedType.leafComponentType().readableName())},
							new String[] {new String(searchedType.leafComponentType().shortReadableName())},
							nameRef.sourceStart,
							(int) nameRef.sourcePositions[index-1]);
						return;
				}
				String fieldName = new String(nameRef.tokens[index]);
				String[] arguments = new String[] {fieldName };
				this.handle(
						id,
						arguments,
						arguments,
						nodeSourceStart(field, nameRef),
						nodeSourceEnd(field, nameRef));
				return;
			case ProblemReasons.NotVisible :
				fieldName = new String(nameRef.tokens[index]);
				this.handle(
					IProblem.NotVisibleField,
					new String[] {fieldName, new String(field.declaringClass.readableName())},
					new String[] {fieldName, new String(field.declaringClass.shortReadableName())},
					nodeSourceStart(field, nameRef),
					nodeSourceEnd(field, nameRef));
				return;
			case ProblemReasons.Ambiguous :
				id = IProblem.AmbiguousField;
				break;
			case ProblemReasons.NonStaticReferenceInStaticContext :
				id = IProblem.NonStaticFieldFromStaticInvocation;
				break;
			case ProblemReasons.NonStaticReferenceInConstructorInvocation :
				id = IProblem.InstanceFieldDuringConstructorInvocation;
				break;
			case ProblemReasons.InheritedNameHidesEnclosingName :
				id = IProblem.InheritedFieldHidesEnclosingName;
				break;
			case ProblemReasons.ReceiverTypeNotVisible :
				this.handle(
					IProblem.NotVisibleType,
					new String[] {new String(searchedType.leafComponentType().readableName())},
					new String[] {new String(searchedType.leafComponentType().shortReadableName())},
					nameRef.sourceStart,
					(int) nameRef.sourcePositions[index-1]);
				return;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(nameRef); // want to fail to see why we were here...
				break;
		}
		String[] arguments = new String[] {CharOperation.toString(CharOperation.subarray(nameRef.tokens, 0, index + 1))};
		this.handle(
			id,
			arguments,
			arguments,
			nameRef.sourceStart,
			(int) nameRef.sourcePositions[index]);
	}
	
	public void invalidFileNameForPackageAnnotations(Annotation annotation) {
		this.handle(
				IProblem.InvalidFileNameForPackageAnnotations,
				NoArgument,
				NoArgument,
				annotation.sourceStart,
				annotation.sourceEnd);
	}
	
	public void invalidMethod(MessageSend messageSend, MethodBinding method) {
		if (isRecoveredName(messageSend.selector)) return;
	
		int id = IProblem.UndefinedMethod; //default...
	    MethodBinding shownMethod = method;
		switch (method.problemId()) {
			case ProblemReasons.NotFound :
				if ((method.declaringClass.tagBits & TagBits.HasMissingType) != 0) {
					this.handle(
							IProblem.UndefinedType,
							new String[] {new String(method.declaringClass.readableName())},
							new String[] {new String(method.declaringClass.shortReadableName())},
							messageSend.receiver.sourceStart,
							messageSend.receiver.sourceEnd);
						return;
				}
				id = IProblem.UndefinedMethod;
				ProblemMethodBinding problemMethod = (ProblemMethodBinding) method;
				if (problemMethod.closestMatch != null) {
				    	shownMethod = problemMethod.closestMatch;
				    	if ((shownMethod.tagBits & TagBits.HasMissingType) != 0) {
							missingTypeInMethod(messageSend, shownMethod);
							return;
				    	}
						String closestParameterTypeNames = typesAsString(shownMethod, false);
						String parameterTypeNames = typesAsString(problemMethod.parameters, false);
						String closestParameterTypeShortNames = typesAsString(shownMethod, true);
						String parameterTypeShortNames = typesAsString(problemMethod.parameters, true);
						if (closestParameterTypeNames.equals(parameterTypeNames)) {
							// include null annotations, maybe they show the difference:
							closestParameterTypeNames = typesAsString(shownMethod, false, true);
							parameterTypeNames = typesAsString(problemMethod.parameters, false, true);
							closestParameterTypeShortNames = typesAsString(shownMethod, true, true);
							parameterTypeShortNames = typesAsString(problemMethod.parameters, true, true);
						}
						if (closestParameterTypeShortNames.equals(parameterTypeShortNames)) {
							closestParameterTypeShortNames = closestParameterTypeNames;
							parameterTypeShortNames = parameterTypeNames;
						}
						this.handle(
							IProblem.ParameterMismatch,
							new String[] {
								new String(shownMethod.declaringClass.readableName()),
								new String(shownMethod.selector),
								closestParameterTypeNames,
								parameterTypeNames
							},
							new String[] {
								new String(shownMethod.declaringClass.shortReadableName()),
								new String(shownMethod.selector),
								closestParameterTypeShortNames,
								parameterTypeShortNames
							},
							(int) (messageSend.nameSourcePosition >>> 32),
							(int) messageSend.nameSourcePosition);
						return;
				}
				break;
			case ProblemReasons.NotVisible :
				id = IProblem.NotVisibleMethod;
				problemMethod = (ProblemMethodBinding) method;
				if (problemMethod.closestMatch != null) {
				    shownMethod = problemMethod.closestMatch.original();
			    }
				break;
			case ProblemReasons.Ambiguous :
				id = IProblem.AmbiguousMethod;
				break;
			case ProblemReasons.InheritedNameHidesEnclosingName :
				id = IProblem.InheritedMethodHidesEnclosingName;
				break;
			case ProblemReasons.NonStaticReferenceInConstructorInvocation :
				id = IProblem.InstanceMethodDuringConstructorInvocation;
				break;
			case ProblemReasons.NonStaticReferenceInStaticContext :
				id = IProblem.StaticMethodRequested;
				break;
			case ProblemReasons.NonStaticOrAlienTypeReceiver:
				this.handle(
						IProblem.NonStaticOrAlienTypeReceiver,
						new String[] {
								new String(method.declaringClass.readableName()),
						        new String(method.selector),
						},
						new String[] {
								new String(method.declaringClass.shortReadableName()),
						        new String(method.selector),
						},
						(int) (messageSend.nameSourcePosition >>> 32),
						(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.ReceiverTypeNotVisible :
				this.handle(
					IProblem.NotVisibleType,	// cannot occur in javadoc comments
					new String[] {new String(method.declaringClass.readableName())},
					new String[] {new String(method.declaringClass.shortReadableName())},
					messageSend.receiver.sourceStart,
					messageSend.receiver.sourceEnd);
				return;
			case ProblemReasons.ParameterBoundMismatch :
				problemMethod = (ProblemMethodBinding) method;
				ParameterizedGenericMethodBinding substitutedMethod = (ParameterizedGenericMethodBinding) problemMethod.closestMatch;
				shownMethod = substitutedMethod.original();
				int augmentedLength = problemMethod.parameters.length;
				TypeBinding inferredTypeArgument = problemMethod.parameters[augmentedLength-2];
				TypeVariableBinding typeParameter = (TypeVariableBinding) problemMethod.parameters[augmentedLength-1];
				TypeBinding[] invocationArguments = new TypeBinding[augmentedLength-2]; // remove extra info from the end
				System.arraycopy(problemMethod.parameters, 0, invocationArguments, 0, augmentedLength-2);
				this.handle(
					IProblem.GenericMethodTypeArgumentMismatch,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
					        typesAsString(invocationArguments, false),
					        new String(inferredTypeArgument.readableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, false) },
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
					        typesAsString(invocationArguments, true),
					        new String(inferredTypeArgument.shortReadableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, true) },
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.TypeParameterArityMismatch :
				problemMethod = (ProblemMethodBinding) method;
				shownMethod = problemMethod.closestMatch;
				if (shownMethod.typeVariables == Binding.NO_TYPE_VARIABLES) {
					this.handle(
						IProblem.NonGenericMethod ,
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, false),
						        new String(shownMethod.declaringClass.readableName()),
						        typesAsString(method, false) },
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, true),
						        new String(shownMethod.declaringClass.shortReadableName()),
						        typesAsString(method, true) },
						(int) (messageSend.nameSourcePosition >>> 32),
						(int) messageSend.nameSourcePosition);
				} else {
					this.handle(
						IProblem.IncorrectArityForParameterizedMethod  ,
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, false),
						        new String(shownMethod.declaringClass.readableName()),
								typesAsString(shownMethod.typeVariables, false),
						        typesAsString(method, false) },
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, true),
						        new String(shownMethod.declaringClass.shortReadableName()),
								typesAsString(shownMethod.typeVariables, true),
						        typesAsString(method, true) },
						(int) (messageSend.nameSourcePosition >>> 32),
						(int) messageSend.nameSourcePosition);
				}
				return;
			case ProblemReasons.ParameterizedMethodTypeMismatch :
				problemMethod = (ProblemMethodBinding) method;
				shownMethod = problemMethod.closestMatch;
				this.handle(
					IProblem.ParameterizedMethodArgumentTypeMismatch,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownMethod).typeArguments, false),
					        typesAsString(method, false) },
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownMethod).typeArguments, true),
					        typesAsString(method, true) },
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.TypeArgumentsForRawGenericMethod :
				problemMethod = (ProblemMethodBinding) method;
				shownMethod = problemMethod.closestMatch;
				this.handle(
					IProblem.TypeArgumentsForRawGenericMethod ,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
					        typesAsString(method, false) },
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
					        typesAsString(method, true) },
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.ParameterizedMethodExpectedTypeProblem:
				// FIXME(stephan): construct suitable message (https://bugs.eclipse.org/404675)
				problemMethod = (ProblemMethodBinding) method;
				InferenceContext18 inferenceContext = problemMethod.inferenceContext;
				if (inferenceContext != null && inferenceContext.outerContext != null) {
					// problem relates to a nested inference context, let the outer handle it:
					inferenceContext.outerContext.addProblemMethod(problemMethod);
					return;
				}
				shownMethod = problemMethod.closestMatch;
				this.handle(
					IProblem.TypeMismatch,
					new String[] {
					        String.valueOf(shownMethod.returnType.readableName()),
					        (problemMethod.returnType != null ? String.valueOf(problemMethod.returnType.readableName()) : "<unknown>")}, //$NON-NLS-1$
					new String[] {
					        String.valueOf(shownMethod.returnType.shortReadableName()),
					        (problemMethod.returnType != null ? String.valueOf(problemMethod.returnType.shortReadableName()) : "<unknown>")}, //$NON-NLS-1$
					messageSend.sourceStart,
					messageSend.sourceEnd);
				return;
			case ProblemReasons.VarargsElementTypeNotVisible: // https://bugs.eclipse.org/bugs/show_bug.cgi?id=346042
				problemMethod = (ProblemMethodBinding) method;
				if (problemMethod.closestMatch != null) {
				    shownMethod = problemMethod.closestMatch.original();
			    }
				TypeBinding varargsElementType = shownMethod.parameters[shownMethod.parameters.length - 1].leafComponentType();
				this.handle(
					IProblem.VarargsElementTypeNotVisible,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
					        new String(varargsElementType.readableName())
					},
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
					        new String(varargsElementType.shortReadableName())
					},
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.ApplicableMethodOverriddenByInapplicable:
				problemMethod = (ProblemMethodBinding) method;
				if (problemMethod.closestMatch != null) {
				    shownMethod = problemMethod.closestMatch.original();
			    }
				this.handle(
					IProblem.ApplicableMethodOverriddenByInapplicable,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
					},
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
					},
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.ContradictoryNullAnnotations:
				problemMethod = (ProblemMethodBinding) method;
				contradictoryNullAnnotationsInferred(problemMethod.closestMatch, (ASTNode)messageSend);
				return;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(messageSend); // want to fail to see why we were here...
				break;
		}
		this.handle(
			id,
			new String[] {
				new String(method.declaringClass.readableName()),
				new String(shownMethod.selector), typesAsString(shownMethod, false)},
			new String[] {
				new String(method.declaringClass.shortReadableName()),
				new String(shownMethod.selector), typesAsString(shownMethod, true)},
			(int) (messageSend.nameSourcePosition >>> 32),
			(int) messageSend.nameSourcePosition);
	}
	public void invalidNullToSynchronize(Expression expression) {
		this.handle(
			IProblem.InvalidNullToSynchronized,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidOperator(BinaryExpression expression, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.InvalidOperator,
			new String[] {
				expression.operatorToString(),
				leftName + ", " + rightName}, //$NON-NLS-1$
			new String[] {
				expression.operatorToString(),
				leftShortName + ", " + rightShortName}, //$NON-NLS-1$
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidOperator(CompoundAssignment assign, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.InvalidOperator,
			new String[] {
				assign.operatorToString(),
				leftName + ", " + rightName}, //$NON-NLS-1$
			new String[] {
				assign.operatorToString(),
				leftShortName + ", " + rightShortName}, //$NON-NLS-1$
			assign.sourceStart,
			assign.sourceEnd);
	}
	public void invalidOperator(UnaryExpression expression, TypeBinding type) {
		this.handle(
			IProblem.InvalidOperator,
			new String[] {expression.operatorToString(), new String(type.readableName())},
			new String[] {expression.operatorToString(), new String(type.shortReadableName())},
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidParameterizedExceptionType(TypeBinding exceptionType, ASTNode location) {
		this.handle(
			IProblem.InvalidParameterizedExceptionType,
			new String[] {new String(exceptionType.readableName())},
			new String[] {new String(exceptionType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void invalidParenthesizedExpression(ASTNode reference) {
		this.handle(
			IProblem.InvalidParenthesizedExpression,
			NoArgument,
			NoArgument,
			reference.sourceStart,
			reference.sourceEnd);
	}
	public void invalidType(ASTNode location, TypeBinding type) {
		if (type instanceof ReferenceBinding) {
			if (isRecoveredName(((ReferenceBinding)type).compoundName)) return;
		}
		else if (type instanceof ArrayBinding) {
			TypeBinding leafType = ((ArrayBinding)type).leafComponentType;
			if (leafType instanceof ReferenceBinding) {
				if (isRecoveredName(((ReferenceBinding)leafType).compoundName)) return;
			}
		}
	
		if (type.isParameterizedType()) {
			List missingTypes = type.collectMissingTypes(null);
			if (missingTypes != null) {
				ReferenceContext savedContext = this.referenceContext;
				for (Iterator iterator = missingTypes.iterator(); iterator.hasNext(); ) {
					try {
						invalidType(location, (TypeBinding) iterator.next());
					} finally {
						this.referenceContext = savedContext;
					}
				}
				return;
			}
		}
		int id = IProblem.UndefinedType; // default
		switch (type.problemId()) {
			case ProblemReasons.NotFound :
				id = IProblem.UndefinedType;
				break;
			case ProblemReasons.NotVisible :
				id = IProblem.NotVisibleType;
				break;
			case ProblemReasons.Ambiguous :
				id = IProblem.AmbiguousType;
				break;
			case ProblemReasons.InternalNameProvided :
				id = IProblem.InternalTypeNameProvided;
				break;
			case ProblemReasons.InheritedNameHidesEnclosingName :
				id = IProblem.InheritedTypeHidesEnclosingName;
				break;
			case ProblemReasons.NonStaticReferenceInStaticContext :
				id = IProblem.NonStaticTypeFromStaticInvocation;
				break;
			case ProblemReasons.IllegalSuperTypeVariable :
				id = IProblem.IllegalTypeVariableSuperReference;
				break;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(location); // want to fail to see why we were here...
				break;
		}
	
		int end = location.sourceEnd;
		if (location instanceof QualifiedNameReference) {
			QualifiedNameReference ref = (QualifiedNameReference) location;
			if (isRecoveredName(ref.tokens)) return;
			if (ref.indexOfFirstFieldBinding >= 1)
				end = (int) ref.sourcePositions[ref.indexOfFirstFieldBinding - 1];
		} else if (location instanceof ParameterizedQualifiedTypeReference) {
			// must be before instanceof ArrayQualifiedTypeReference
			ParameterizedQualifiedTypeReference ref = (ParameterizedQualifiedTypeReference) location;
			if (isRecoveredName(ref.tokens)) return;
			if (type instanceof ReferenceBinding) {
				char[][] name = ((ReferenceBinding) type).compoundName;
				end = (int) ref.sourcePositions[name.length - 1];
			}
		} else if (location instanceof ArrayQualifiedTypeReference) {
			ArrayQualifiedTypeReference arrayQualifiedTypeReference = (ArrayQualifiedTypeReference) location;
			if (isRecoveredName(arrayQualifiedTypeReference.tokens)) return;
			TypeBinding leafType = type.leafComponentType();
			if (leafType instanceof ReferenceBinding) {
				char[][] name = ((ReferenceBinding) leafType).compoundName; // problem type will tell how much got resolved
				end = (int) arrayQualifiedTypeReference.sourcePositions[name.length-1];
			} else {
				long[] positions = arrayQualifiedTypeReference.sourcePositions;
				end = (int) positions[positions.length - 1];
			}
		} else if (location instanceof QualifiedTypeReference) {
			QualifiedTypeReference ref = (QualifiedTypeReference) location;
			if (isRecoveredName(ref.tokens)) return;
			if (type instanceof ReferenceBinding) {
				char[][] name = ((ReferenceBinding) type).compoundName;
				if (name.length <= ref.sourcePositions.length)
					end = (int) ref.sourcePositions[name.length - 1];
			}
		} else if (location instanceof ImportReference) {
			ImportReference ref = (ImportReference) location;
			if (isRecoveredName(ref.tokens)) return;
			if (type instanceof ReferenceBinding) {
				char[][] name = ((ReferenceBinding) type).compoundName;
				end = (int) ref.sourcePositions[name.length - 1];
			}
		} else if (location instanceof ArrayTypeReference) {
			ArrayTypeReference arrayTypeReference = (ArrayTypeReference) location;
			if (isRecoveredName(arrayTypeReference.token)) return;
			end = arrayTypeReference.originalSourceEnd;
		}
	
		int start = location.sourceStart;
		if (location instanceof org.summer.sdt.internal.compiler.ast.SingleTypeReference) {
			org.summer.sdt.internal.compiler.ast.SingleTypeReference ref =
					(org.summer.sdt.internal.compiler.ast.SingleTypeReference) location;
			if (ref.annotations != null)
				start = end - ref.token.length + 1;
		} else if (location instanceof QualifiedTypeReference) {
			QualifiedTypeReference ref = (QualifiedTypeReference) location;
			if (ref.annotations != null)
				start = (int) (ref.sourcePositions[0] & 0x00000000FFFFFFFFL ) - ref.tokens[0].length + 1;
		}
	
		this.handle(
			id,
			new String[] {new String(type.leafComponentType().readableName()) },
			new String[] {new String(type.leafComponentType().shortReadableName())},
			start,
			end);
	}
	public void invalidTypeForCollection(Expression expression) {
		this.handle(
				IProblem.InvalidTypeForCollection,
				NoArgument,
				NoArgument,
				expression.sourceStart,
				expression.sourceEnd);
	}
	public void invalidTypeForCollectionTarget14(Expression expression) {
		this.handle(
				IProblem.InvalidTypeForCollectionTarget14,
				NoArgument,
				NoArgument,
				expression.sourceStart,
				expression.sourceEnd);
	}
	public void invalidTypeToSynchronize(Expression expression, TypeBinding type) {
		this.handle(
			IProblem.InvalidTypeToSynchronized,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidTypeVariableAsException(TypeBinding exceptionType, ASTNode location) {
		this.handle(
			IProblem.InvalidTypeVariableExceptionType,
			new String[] {new String(exceptionType.readableName())},
			new String[] {new String(exceptionType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void invalidUnaryExpression(Expression expression) {
		this.handle(
			IProblem.InvalidUnaryExpression,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void invalidUsageOfAnnotation(Annotation annotation) {
		this.handle(
			IProblem.InvalidUsageOfAnnotations,
			NoArgument,
			NoArgument,
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	public void invalidUsageOfAnnotationDeclarations(TypeDeclaration annotationTypeDeclaration) {
		this.handle(
			IProblem.InvalidUsageOfAnnotationDeclarations,
			NoArgument,
			NoArgument,
			annotationTypeDeclaration.sourceStart,
			annotationTypeDeclaration.sourceEnd);
	}
	public void invalidUsageOfEnumDeclarations(TypeDeclaration enumDeclaration) {
		this.handle(
			IProblem.InvalidUsageOfEnumDeclarations,
			NoArgument,
			NoArgument,
			enumDeclaration.sourceStart,
			enumDeclaration.sourceEnd);
	}
	public void invalidUsageOfForeachStatements(LocalDeclaration elementVariable, Expression collection) {
		this.handle(
			IProblem.InvalidUsageOfForeachStatements,
			NoArgument,
			NoArgument,
			elementVariable.declarationSourceStart,
			collection.sourceEnd);
	}
	public void invalidUsageOfStaticImports(ImportReference staticImport) {
		this.handle(
			IProblem.InvalidUsageOfStaticImports,
			NoArgument,
			NoArgument,
			staticImport.declarationSourceStart,
			staticImport.declarationSourceEnd);
	}
	public void invalidUsageOfTypeArguments(TypeReference firstTypeReference, TypeReference lastTypeReference) {
		this.handle(
			IProblem.InvalidUsageOfTypeArguments,
			NoArgument,
			NoArgument,
			firstTypeReference.sourceStart,
			lastTypeReference.sourceEnd);
	}
	public void invalidUsageOfTypeParameters(TypeParameter firstTypeParameter, TypeParameter lastTypeParameter) {
		this.handle(
			IProblem.InvalidUsageOfTypeParameters,
			NoArgument,
			NoArgument,
			firstTypeParameter.declarationSourceStart,
			lastTypeParameter.declarationSourceEnd);
	}
	public void invalidUsageOfTypeParametersForAnnotationDeclaration(TypeDeclaration annotationTypeDeclaration) {
		TypeParameter[] parameters = annotationTypeDeclaration.typeParameters;
		int length = parameters.length;
		this.handle(
				IProblem.InvalidUsageOfTypeParametersForAnnotationDeclaration,
				NoArgument,
				NoArgument,
				parameters[0].declarationSourceStart,
				parameters[length - 1].declarationSourceEnd);
	}
	public void invalidUsageOfTypeParametersForEnumDeclaration(TypeDeclaration annotationTypeDeclaration) {
		TypeParameter[] parameters = annotationTypeDeclaration.typeParameters;
		int length = parameters.length;
		this.handle(
				IProblem.InvalidUsageOfTypeParametersForEnumDeclaration,
				NoArgument,
				NoArgument,
				parameters[0].declarationSourceStart,
				parameters[length - 1].declarationSourceEnd);
	}
	public void invalidUsageOfVarargs(Argument argument) {
		this.handle(
			IProblem.InvalidUsageOfVarargs,
			NoArgument,
			NoArgument,
			argument.type.sourceStart,
			argument.sourceEnd);
	}
	
	public void invalidUsageOfTypeAnnotations(Annotation annotation) {
		this.handle(
				IProblem.InvalidUsageOfTypeAnnotations,
				NoArgument,
				NoArgument,
				annotation.sourceStart,
				annotation.sourceEnd);
	}
	public void toleratedMisplacedTypeAnnotations(Annotation first, Annotation last) {
		this.handle(
				IProblem.ToleratedMisplacedTypeAnnotations,
				NoArgument,
				NoArgument,
				first.sourceStart,
				last.sourceEnd);	
	}
	public void misplacedTypeAnnotations(Annotation first, Annotation last) {
		this.handle(
				IProblem.MisplacedTypeAnnotations,
				NoArgument,
				NoArgument,
				first.sourceStart,
				last.sourceEnd);	
	}
	public void illegalUsageOfTypeAnnotations(Annotation annotation) {
		this.handle(
				IProblem.IllegalUsageOfTypeAnnotations,
				NoArgument,
				NoArgument,
				annotation.sourceStart,
				annotation.sourceEnd);	
	}
	public void illegalTypeAnnotationsInStaticMemberAccess(Annotation first, Annotation last) {
		this.handle(
				IProblem.IllegalTypeAnnotationsInStaticMemberAccess,
				NoArgument,
				NoArgument,
				first.sourceStart,
				last.sourceEnd);
	}
	public void isClassPathCorrect(char[][] wellKnownTypeName, CompilationUnitDeclaration compUnitDecl, Object location) {
		this.referenceContext = compUnitDecl;
		String[] arguments = new String[] {CharOperation.toString(wellKnownTypeName)};
		int start = 0, end = 0;
		if (location != null) {
			if (location instanceof InvocationSite) {
				InvocationSite site = (InvocationSite) location;
				start = site.sourceStart();
				end = site.sourceEnd();
			} else if (location instanceof ASTNode) {
				ASTNode node = (ASTNode) location;
				start = node.sourceStart();
				end = node.sourceEnd();
			}
		}
		this.handle(
			IProblem.IsClassPathCorrect,
			arguments,
			arguments,
			start,
			end);
	}
	private boolean isIdentifier(int token) {
		return token == TerminalTokens.TokenNameIdentifier;
	}
	private boolean isKeyword(int token) {
		switch(token) {
			case TerminalTokens.TokenNameabstract:
			case TerminalTokens.TokenNameassert:
			case TerminalTokens.TokenNamebyte:
			case TerminalTokens.TokenNamebreak:
			case TerminalTokens.TokenNameboolean:
			case TerminalTokens.TokenNamecase:
			case TerminalTokens.TokenNamechar:
			case TerminalTokens.TokenNamecatch:
			case TerminalTokens.TokenNameclass:
			case TerminalTokens.TokenNamecontinue:
			case TerminalTokens.TokenNamedo:
			case TerminalTokens.TokenNamedouble:
			case TerminalTokens.TokenNamedefault:
			case TerminalTokens.TokenNameelse:
			case TerminalTokens.TokenNameextends:
			case TerminalTokens.TokenNamefor:
			case TerminalTokens.TokenNamefinal:
			case TerminalTokens.TokenNamefloat:
			case TerminalTokens.TokenNamefalse:
			case TerminalTokens.TokenNamefinally:
			case TerminalTokens.TokenNameif:
			case TerminalTokens.TokenNameint:
			case TerminalTokens.TokenNameimport:
			case TerminalTokens.TokenNameinterface:
			case TerminalTokens.TokenNameimplements:
			case TerminalTokens.TokenNameinstanceof:
			case TerminalTokens.TokenNamelong:
			case TerminalTokens.TokenNamenew:
			case TerminalTokens.TokenNamenull:
			case TerminalTokens.TokenNamenative:
			case TerminalTokens.TokenNamepublic:
			case TerminalTokens.TokenNamepackage:
			case TerminalTokens.TokenNameprivate:
			case TerminalTokens.TokenNameprotected:
			case TerminalTokens.TokenNamereturn:
			case TerminalTokens.TokenNameshort:
			case TerminalTokens.TokenNamesuper:
			case TerminalTokens.TokenNamestatic:
			case TerminalTokens.TokenNameswitch:
			case TerminalTokens.TokenNamestrictfp:
			case TerminalTokens.TokenNamesynchronized:
			case TerminalTokens.TokenNametry:
			case TerminalTokens.TokenNamethis:
			case TerminalTokens.TokenNametrue:
			case TerminalTokens.TokenNamethrow:
			case TerminalTokens.TokenNamethrows:
			case TerminalTokens.TokenNametransient:
			case TerminalTokens.TokenNamevoid:
			case TerminalTokens.TokenNamevolatile:
			case TerminalTokens.TokenNamewhile:
				return true;
			default:
				return false;
		}
	}
	private boolean isLiteral(int token) {
		return Scanner.isLiteral(token);
	}
	
	private boolean isRecoveredName(char[] simpleName) {
		return simpleName == RecoveryScanner.FAKE_IDENTIFIER;
	}
	
	private boolean isRecoveredName(char[][] qualifiedName) {
		if(qualifiedName == null) return false;
		for (int i = 0; i < qualifiedName.length; i++) {
			if(qualifiedName[i] == RecoveryScanner.FAKE_IDENTIFIER) return true;
		}
		return false;
	}
	
	public void javadocAmbiguousMethodReference(int sourceStart, int sourceEnd, Binding fieldBinding, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocAmbiguousMethodReference);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] {new String(fieldBinding.readableName())};
			handle(
				IProblem.JavadocAmbiguousMethodReference,
				arguments,
				arguments,
				severity,
				sourceStart,
				sourceEnd);
		}
	}
	
	public void javadocDeprecatedField(FieldBinding field, ASTNode location, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocUsingDeprecatedField);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			this.handle(
				IProblem.JavadocUsingDeprecatedField,
				new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
				new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
				severity,
				nodeSourceStart(field, location),
				nodeSourceEnd(field, location));
		}
	}
	
	public void javadocDeprecatedMethod(MethodBinding method, ASTNode location, int modifiers) {
		boolean isConstructor = method.isConstructor();
		int severity = computeSeverity(isConstructor ? IProblem.JavadocUsingDeprecatedConstructor : IProblem.JavadocUsingDeprecatedMethod);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			if (isConstructor) {
				this.handle(
					IProblem.JavadocUsingDeprecatedConstructor,
					new String[] {new String(method.declaringClass.readableName()), typesAsString(method, false)},
					new String[] {new String(method.declaringClass.shortReadableName()), typesAsString(method, true)},
					severity,
					location.sourceStart,
					location.sourceEnd);
			} else {
				this.handle(
					IProblem.JavadocUsingDeprecatedMethod,
					new String[] {new String(method.declaringClass.readableName()), new String(method.selector), typesAsString(method, false)},
					new String[] {new String(method.declaringClass.shortReadableName()), new String(method.selector), typesAsString(method, true)},
					severity,
					location.sourceStart,
					location.sourceEnd);
			}
		}
	}
	public void javadocDeprecatedType(TypeBinding type, ASTNode location, int modifiers) {
		javadocDeprecatedType(type, location, modifiers, Integer.MAX_VALUE);
	}
	public void javadocDeprecatedType(TypeBinding type, ASTNode location, int modifiers, int index) {
		if (location == null) return; // 1G828DN - no type ref for synthetic arguments
		int severity = computeSeverity(IProblem.JavadocUsingDeprecatedType);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			if (type.isMemberType() && type instanceof ReferenceBinding && !javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, ((ReferenceBinding)type).modifiers)) {
				this.handle(IProblem.JavadocHiddenReference, NoArgument, NoArgument, location.sourceStart, location.sourceEnd);
			} else {
				this.handle(
					IProblem.JavadocUsingDeprecatedType,
					new String[] {new String(type.readableName())},
					new String[] {new String(type.shortReadableName())},
					severity,
					location.sourceStart,
					nodeSourceEnd(null, location, index));
			}
		}
	}
	public void javadocDuplicatedParamTag(char[] token, int sourceStart, int sourceEnd, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocDuplicateParamName);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] {String.valueOf(token)};
			this.handle(
				IProblem.JavadocDuplicateParamName,
				arguments,
				arguments,
				severity,
				sourceStart,
				sourceEnd);
		}
	}
	public void javadocDuplicatedReturnTag(int sourceStart, int sourceEnd){
		this.handle(IProblem.JavadocDuplicateReturnTag, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocDuplicatedTag(char[] tagName, int sourceStart, int sourceEnd){
		String[] arguments = new String[] { new String(tagName) };
		this.handle(
			IProblem.JavadocDuplicateTag,
			arguments,
			arguments,
			sourceStart,
			sourceEnd);
	}
	public void javadocDuplicatedThrowsClassName(TypeReference typeReference, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocDuplicateThrowsClassName);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] {String.valueOf(typeReference.resolvedType.sourceName())};
			this.handle(
				IProblem.JavadocDuplicateThrowsClassName,
				arguments,
				arguments,
				severity,
				typeReference.sourceStart,
				typeReference.sourceEnd);
		}
	}
	public void javadocEmptyReturnTag(int sourceStart, int sourceEnd, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocEmptyReturnTag);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] { new String(JavadocTagConstants.TAG_RETURN) };
			this.handle(IProblem.JavadocEmptyReturnTag, arguments, arguments, sourceStart, sourceEnd);
		}
	}
	public void javadocErrorNoMethodFor(MessageSend messageSend, TypeBinding recType, TypeBinding[] params, int modifiers) {
		int id = recType.isArrayType() ? IProblem.JavadocNoMessageSendOnArrayType : IProblem.JavadocNoMessageSendOnBaseType;
		int severity = computeSeverity(id);
		if (severity == ProblemSeverities.Ignore) return;
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		for (int i = 0, length = params.length; i < length; i++) {
			if (i != 0){
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(params[i].readableName()));
			shortBuffer.append(new String(params[i].shortReadableName()));
		}
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			this.handle(
				id,
				new String[] {new String(recType.readableName()), new String(messageSend.selector), buffer.toString()},
				new String[] {new String(recType.shortReadableName()), new String(messageSend.selector), shortBuffer.toString()},
				severity,
				messageSend.sourceStart,
				messageSend.sourceEnd);
		}
	}
	public void javadocHiddenReference(int sourceStart, int sourceEnd, Scope scope, int modifiers) {
		Scope currentScope = scope;
		while (currentScope.parent.kind != Scope.COMPILATION_UNIT_SCOPE ) {
			if (!javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, currentScope.getDeclarationModifiers())) {
				return;
			}
			currentScope = currentScope.parent;
		}
		String[] arguments = new String[] { this.options.getVisibilityString(this.options.reportInvalidJavadocTagsVisibility), this.options.getVisibilityString(modifiers) };
		this.handle(IProblem.JavadocHiddenReference, arguments, arguments, sourceStart, sourceEnd);
	}
	public void javadocInvalidConstructor(Statement statement, MethodBinding targetConstructor, int modifiers) {
	
		if (!javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) return;
		int sourceStart = statement.sourceStart;
		int sourceEnd = statement.sourceEnd;
		if (statement instanceof AllocationExpression) {
			AllocationExpression allocation = (AllocationExpression)statement;
			if (allocation.enumConstant != null) {
				sourceStart = allocation.enumConstant.sourceStart;
				sourceEnd = allocation.enumConstant.sourceEnd;
			}
		}
		int id = IProblem.JavadocUndefinedConstructor; //default...
		ProblemMethodBinding problemConstructor = null;
		MethodBinding shownConstructor = null;
		switch (targetConstructor.problemId()) {
			case ProblemReasons.NotFound :
				id = IProblem.JavadocUndefinedConstructor;
				break;
			case ProblemReasons.NotVisible :
				id = IProblem.JavadocNotVisibleConstructor;
				break;
			case ProblemReasons.Ambiguous :
				id = IProblem.JavadocAmbiguousConstructor;
				break;
			case ProblemReasons.ParameterBoundMismatch :
				int severity = computeSeverity(IProblem.JavadocGenericConstructorTypeArgumentMismatch);
				if (severity == ProblemSeverities.Ignore) return;
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				ParameterizedGenericMethodBinding substitutedConstructor = (ParameterizedGenericMethodBinding) problemConstructor.closestMatch;
				shownConstructor = substitutedConstructor.original();
	
				int augmentedLength = problemConstructor.parameters.length;
				TypeBinding inferredTypeArgument = problemConstructor.parameters[augmentedLength-2];
				TypeVariableBinding typeParameter = (TypeVariableBinding) problemConstructor.parameters[augmentedLength-1];
				TypeBinding[] invocationArguments = new TypeBinding[augmentedLength-2]; // remove extra info from the end
				System.arraycopy(problemConstructor.parameters, 0, invocationArguments, 0, augmentedLength-2);
	
				this.handle(
					IProblem.JavadocGenericConstructorTypeArgumentMismatch,
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, false),
					        new String(shownConstructor.declaringClass.readableName()),
					        typesAsString(invocationArguments, false),
					        new String(inferredTypeArgument.readableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, false) },
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, true),
					        new String(shownConstructor.declaringClass.shortReadableName()),
					        typesAsString(invocationArguments, true),
					        new String(inferredTypeArgument.shortReadableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, true) },
					severity,
					sourceStart,
					sourceEnd);
				return;
	
			case ProblemReasons.TypeParameterArityMismatch :
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				boolean noTypeVariables = shownConstructor.typeVariables == Binding.NO_TYPE_VARIABLES;
				severity = computeSeverity(noTypeVariables ? IProblem.JavadocNonGenericConstructor : IProblem.JavadocIncorrectArityForParameterizedConstructor);
				if (severity == ProblemSeverities.Ignore) return;
				if (noTypeVariables) {
					this.handle(
						IProblem.JavadocNonGenericConstructor,
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, false),
						        new String(shownConstructor.declaringClass.readableName()),
						        typesAsString(targetConstructor, false) },
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, true),
						        new String(shownConstructor.declaringClass.shortReadableName()),
						        typesAsString(targetConstructor, true) },
						severity,
						sourceStart,
						sourceEnd);
				} else {
					this.handle(
						IProblem.JavadocIncorrectArityForParameterizedConstructor,
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, false),
						        new String(shownConstructor.declaringClass.readableName()),
								typesAsString(shownConstructor.typeVariables, false),
						        typesAsString(targetConstructor, false) },
						new String[] {
						        new String(shownConstructor.declaringClass.sourceName()),
						        typesAsString(shownConstructor, true),
						        new String(shownConstructor.declaringClass.shortReadableName()),
								typesAsString(shownConstructor.typeVariables, true),
						        typesAsString(targetConstructor, true) },
						severity,
						sourceStart,
						sourceEnd);
				}
				return;
			case ProblemReasons.ParameterizedMethodTypeMismatch :
				severity = computeSeverity(IProblem.JavadocParameterizedConstructorArgumentTypeMismatch);
				if (severity == ProblemSeverities.Ignore) return;
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				this.handle(
					IProblem.JavadocParameterizedConstructorArgumentTypeMismatch,
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, false),
					        new String(shownConstructor.declaringClass.readableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownConstructor).typeArguments, false),
					        typesAsString(targetConstructor, false) },
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, true),
					        new String(shownConstructor.declaringClass.shortReadableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownConstructor).typeArguments, true),
					        typesAsString(targetConstructor, true) },
					severity,
					sourceStart,
					sourceEnd);
				return;
			case ProblemReasons.TypeArgumentsForRawGenericMethod :
				severity = computeSeverity(IProblem.JavadocTypeArgumentsForRawGenericConstructor);
				if (severity == ProblemSeverities.Ignore) return;
				problemConstructor = (ProblemMethodBinding) targetConstructor;
				shownConstructor = problemConstructor.closestMatch;
				this.handle(
					IProblem.JavadocTypeArgumentsForRawGenericConstructor,
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, false),
					        new String(shownConstructor.declaringClass.readableName()),
					        typesAsString(targetConstructor, false) },
					new String[] {
					        new String(shownConstructor.declaringClass.sourceName()),
					        typesAsString(shownConstructor, true),
					        new String(shownConstructor.declaringClass.shortReadableName()),
					        typesAsString(targetConstructor, true) },
					severity,
					sourceStart,
					sourceEnd);
				return;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(statement); // want to fail to see why we were here...
				break;
		}
		int severity = computeSeverity(id);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			id,
			new String[] {new String(targetConstructor.declaringClass.readableName()), typesAsString(targetConstructor, false)},
			new String[] {new String(targetConstructor.declaringClass.shortReadableName()), typesAsString(targetConstructor, true)},
			severity,
			statement.sourceStart,
			statement.sourceEnd);
	}
	/*
	 * Similar implementation than invalidField(FieldReference...)
	 * Note that following problem id cannot occur for Javadoc:
	 * 	- NonStaticReferenceInStaticContext :
	 * 	- NonStaticReferenceInConstructorInvocation :
	 * 	- ReceiverTypeNotVisible :
	 */
	public void javadocInvalidField(FieldReference fieldRef, Binding fieldBinding, TypeBinding searchedType, int modifiers) {
		int id = IProblem.JavadocUndefinedField;
		switch (fieldBinding.problemId()) {
			case ProblemReasons.NotFound :
				id = IProblem.JavadocUndefinedField;
				break;
			case ProblemReasons.NotVisible :
				id = IProblem.JavadocNotVisibleField;
				break;
			case ProblemReasons.Ambiguous :
				id = IProblem.JavadocAmbiguousField;
				break;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(fieldRef); // want to fail to see why we were here...
				break;
		}
		int severity = computeSeverity(id);
		if (severity == ProblemSeverities.Ignore) return;
		// report issue
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] {new String(fieldBinding.readableName())};
			handle(
				id,
				arguments,
				arguments,
				severity,
				fieldRef.sourceStart,
				fieldRef.sourceEnd);
		}
	}
	public void javadocInvalidMemberTypeQualification(int sourceStart, int sourceEnd, int modifiers){
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			this.handle(IProblem.JavadocInvalidMemberTypeQualification, NoArgument, NoArgument, sourceStart, sourceEnd);
		}
	}
	/*
	 * Similar implementation than invalidMethod(MessageSend...)
	 * Note that following problem id cannot occur for Javadoc:
	 * 	- NonStaticReferenceInStaticContext :
	 * 	- NonStaticReferenceInConstructorInvocation :
	 * 	- ReceiverTypeNotVisible :
	 */
	public void javadocInvalidMethod(MessageSend messageSend, MethodBinding method, int modifiers) {
		if (!javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) return;
		// set problem id
		ProblemMethodBinding problemMethod = null;
		MethodBinding shownMethod = null;
		int id = IProblem.JavadocUndefinedMethod; //default...
		switch (method.problemId()) {
			case ProblemReasons.NotFound :
				id = IProblem.JavadocUndefinedMethod;
				problemMethod = (ProblemMethodBinding) method;
				if (problemMethod.closestMatch != null) {
					int severity = computeSeverity(IProblem.JavadocParameterMismatch);
					if (severity == ProblemSeverities.Ignore) return;
					String closestParameterTypeNames = typesAsString(problemMethod.closestMatch, false);
					String parameterTypeNames = typesAsString(method, false);
					String closestParameterTypeShortNames = typesAsString(problemMethod.closestMatch, true);
					String parameterTypeShortNames = typesAsString(method, true);
					if (closestParameterTypeShortNames.equals(parameterTypeShortNames)){
						closestParameterTypeShortNames = closestParameterTypeNames;
						parameterTypeShortNames = parameterTypeNames;
					}
					this.handle(
						IProblem.JavadocParameterMismatch,
						new String[] {
							new String(problemMethod.closestMatch.declaringClass.readableName()),
							new String(problemMethod.closestMatch.selector),
							closestParameterTypeNames,
							parameterTypeNames
						},
						new String[] {
							new String(problemMethod.closestMatch.declaringClass.shortReadableName()),
							new String(problemMethod.closestMatch.selector),
							closestParameterTypeShortNames,
							parameterTypeShortNames
						},
						severity,
						(int) (messageSend.nameSourcePosition >>> 32),
						(int) messageSend.nameSourcePosition);
					return;
				}
				break;
			case ProblemReasons.NotVisible :
				id = IProblem.JavadocNotVisibleMethod;
				break;
			case ProblemReasons.Ambiguous :
				id = IProblem.JavadocAmbiguousMethod;
				break;
			case ProblemReasons.ParameterBoundMismatch :
				int severity = computeSeverity(IProblem.JavadocGenericMethodTypeArgumentMismatch);
				if (severity == ProblemSeverities.Ignore) return;
				problemMethod = (ProblemMethodBinding) method;
				ParameterizedGenericMethodBinding substitutedMethod = (ParameterizedGenericMethodBinding) problemMethod.closestMatch;
				shownMethod = substitutedMethod.original();
				int augmentedLength = problemMethod.parameters.length;
				TypeBinding inferredTypeArgument = problemMethod.parameters[augmentedLength-2];
				TypeVariableBinding typeParameter = (TypeVariableBinding) problemMethod.parameters[augmentedLength-1];
				TypeBinding[] invocationArguments = new TypeBinding[augmentedLength-2]; // remove extra info from the end
				System.arraycopy(problemMethod.parameters, 0, invocationArguments, 0, augmentedLength-2);
				this.handle(
					IProblem.JavadocGenericMethodTypeArgumentMismatch,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
					        typesAsString(invocationArguments, false),
					        new String(inferredTypeArgument.readableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, false) },
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
					        typesAsString(invocationArguments, true),
					        new String(inferredTypeArgument.shortReadableName()),
					        new String(typeParameter.sourceName()),
					        parameterBoundAsString(typeParameter, true) },
					severity,
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.TypeParameterArityMismatch :
				problemMethod = (ProblemMethodBinding) method;
				shownMethod = problemMethod.closestMatch;
				boolean noTypeVariables = shownMethod.typeVariables == Binding.NO_TYPE_VARIABLES;
				severity = computeSeverity(noTypeVariables ? IProblem.JavadocNonGenericMethod : IProblem.JavadocIncorrectArityForParameterizedMethod);
				if (severity == ProblemSeverities.Ignore) return;
				if (noTypeVariables) {
					this.handle(
						IProblem.JavadocNonGenericMethod,
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, false),
						        new String(shownMethod.declaringClass.readableName()),
						        typesAsString(method, false) },
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, true),
						        new String(shownMethod.declaringClass.shortReadableName()),
						        typesAsString(method, true) },
						severity,
						(int) (messageSend.nameSourcePosition >>> 32),
						(int) messageSend.nameSourcePosition);
				} else {
					this.handle(
						IProblem.JavadocIncorrectArityForParameterizedMethod,
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, false),
						        new String(shownMethod.declaringClass.readableName()),
								typesAsString(shownMethod.typeVariables, false),
						        typesAsString(method, false) },
						new String[] {
						        new String(shownMethod.selector),
						        typesAsString(shownMethod, true),
						        new String(shownMethod.declaringClass.shortReadableName()),
								typesAsString(shownMethod.typeVariables, true),
						        typesAsString(method, true) },
						severity,
						(int) (messageSend.nameSourcePosition >>> 32),
						(int) messageSend.nameSourcePosition);
				}
				return;
			case ProblemReasons.ParameterizedMethodTypeMismatch :
				severity = computeSeverity(IProblem.JavadocParameterizedMethodArgumentTypeMismatch);
				if (severity == ProblemSeverities.Ignore) return;
				problemMethod = (ProblemMethodBinding) method;
				shownMethod = problemMethod.closestMatch;
				this.handle(
					IProblem.JavadocParameterizedMethodArgumentTypeMismatch,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownMethod).typeArguments, false),
					        typesAsString(method, false) },
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
							typesAsString(((ParameterizedGenericMethodBinding)shownMethod).typeArguments, true),
					        typesAsString(method, true) },
					severity,
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.TypeArgumentsForRawGenericMethod :
				severity = computeSeverity(IProblem.JavadocTypeArgumentsForRawGenericMethod);
				if (severity == ProblemSeverities.Ignore) return;
				problemMethod = (ProblemMethodBinding) method;
				shownMethod = problemMethod.closestMatch;
				this.handle(
					IProblem.JavadocTypeArgumentsForRawGenericMethod,
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, false),
					        new String(shownMethod.declaringClass.readableName()),
					        typesAsString(method, false) },
					new String[] {
					        new String(shownMethod.selector),
					        typesAsString(shownMethod, true),
					        new String(shownMethod.declaringClass.shortReadableName()),
					        typesAsString(method, true) },
					severity,
					(int) (messageSend.nameSourcePosition >>> 32),
					(int) messageSend.nameSourcePosition);
				return;
			case ProblemReasons.NoError : // 0
			default :
				needImplementation(messageSend); // want to fail to see why we were here...
				break;
		}
		int severity = computeSeverity(id);
		if (severity == ProblemSeverities.Ignore) return;
		// report issue
		this.handle(
			id,
			new String[] {
				new String(method.declaringClass.readableName()),
				new String(method.selector), typesAsString(method, false)},
			new String[] {
				new String(method.declaringClass.shortReadableName()),
				new String(method.selector), typesAsString(method, true)},
			severity,
			(int) (messageSend.nameSourcePosition >>> 32),
			(int) messageSend.nameSourcePosition);
	}
	public void javadocInvalidParamTagName(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidParamTagName, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocInvalidParamTypeParameter(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidParamTagTypeParameter, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocInvalidReference(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidSeeReference, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	/**
	 * Report an invalid reference that does not conform to the href syntax.
	 * Valid syntax example: @see IProblem.JavadocInvalidSeeHref
	 */
	public void javadocInvalidSeeHref(int sourceStart, int sourceEnd) {
	this.handle(IProblem.JavadocInvalidSeeHref, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocInvalidSeeReferenceArgs(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidSeeArgs, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	/**
	 * Report a problem on an invalid URL reference.
	 * Valid syntax example: @see IProblem.JavadocInvalidSeeUrlReference
	 */
	public void javadocInvalidSeeUrlReference(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidSeeUrlReference, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocInvalidTag(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidTag, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocInvalidThrowsClass(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocInvalidThrowsClass, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocInvalidThrowsClassName(TypeReference typeReference, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocInvalidThrowsClassName);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] {String.valueOf(typeReference.resolvedType.sourceName())};
			this.handle(
				IProblem.JavadocInvalidThrowsClassName,
				arguments,
				arguments,
				severity,
				typeReference.sourceStart,
				typeReference.sourceEnd);
		}
	}
	public void javadocInvalidType(ASTNode location, TypeBinding type, int modifiers) {
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			int id = IProblem.JavadocUndefinedType; // default
			switch (type.problemId()) {
				case ProblemReasons.NotFound :
					id = IProblem.JavadocUndefinedType;
					break;
				case ProblemReasons.NotVisible :
					id = IProblem.JavadocNotVisibleType;
					break;
				case ProblemReasons.Ambiguous :
					id = IProblem.JavadocAmbiguousType;
					break;
				case ProblemReasons.InternalNameProvided :
					id = IProblem.JavadocInternalTypeNameProvided;
					break;
				case ProblemReasons.InheritedNameHidesEnclosingName :
					id = IProblem.JavadocInheritedNameHidesEnclosingTypeName;
					break;
				case ProblemReasons.NonStaticReferenceInStaticContext :
					id = IProblem.JavadocNonStaticTypeFromStaticInvocation;
				    break;
				case ProblemReasons.NoError : // 0
				default :
					needImplementation(location); // want to fail to see why we were here...
					break;
			}
			int severity = computeSeverity(id);
			if (severity == ProblemSeverities.Ignore) return;
			this.handle(
				id,
				new String[] {new String(type.readableName())},
				new String[] {new String(type.shortReadableName())},
				severity,
				location.sourceStart,
				location.sourceEnd);
		}
	}
	public void javadocInvalidValueReference(int sourceStart, int sourceEnd, int modifiers) {
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers))
			this.handle(IProblem.JavadocInvalidValueReference, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocMalformedSeeReference(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocMalformedSeeReference, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocMissing(int sourceStart, int sourceEnd, int modifiers){
		int severity = computeSeverity(IProblem.JavadocMissing);
		this.javadocMissing(sourceStart, sourceEnd, severity, modifiers);
	}
	public void javadocMissing(int sourceStart, int sourceEnd, int severity, int modifiers){
		if (severity == ProblemSeverities.Ignore) return;
		boolean overriding = (modifiers & (ExtraCompilerModifiers.AccImplementing|ExtraCompilerModifiers.AccOverriding)) != 0;
		boolean report = (this.options.getSeverity(CompilerOptions.MissingJavadocComments) != ProblemSeverities.Ignore)
						&& (!overriding || this.options.reportMissingJavadocCommentsOverriding);
		if (report) {
			String arg = javadocVisibilityArgument(this.options.reportMissingJavadocCommentsVisibility, modifiers);
			if (arg != null) {
				String[] arguments = new String[] { arg };
				this.handle(
					IProblem.JavadocMissing,
					arguments,
					arguments,
					severity,
					sourceStart,
					sourceEnd);
			}
		}
	}
	public void javadocMissingHashCharacter(int sourceStart, int sourceEnd, String ref){
		int severity = computeSeverity(IProblem.JavadocMissingHashCharacter);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] { ref };
		this.handle(
			IProblem.JavadocMissingHashCharacter,
			arguments,
			arguments,
			severity,
			sourceStart,
			sourceEnd);
	}
	public void javadocMissingIdentifier(int sourceStart, int sourceEnd, int modifiers){
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers))
			this.handle(IProblem.JavadocMissingIdentifier, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocMissingParamName(int sourceStart, int sourceEnd, int modifiers){
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers))
			this.handle(IProblem.JavadocMissingParamName, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocMissingParamTag(char[] name, int sourceStart, int sourceEnd, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocMissingParamTag);
		if (severity == ProblemSeverities.Ignore) return;
		boolean overriding = (modifiers & (ExtraCompilerModifiers.AccImplementing|ExtraCompilerModifiers.AccOverriding)) != 0;
		boolean report = (this.options.getSeverity(CompilerOptions.MissingJavadocTags) != ProblemSeverities.Ignore)
						&& (!overriding || this.options.reportMissingJavadocTagsOverriding);
		if (report && javadocVisibility(this.options.reportMissingJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] { String.valueOf(name) };
			this.handle(
				IProblem.JavadocMissingParamTag,
				arguments,
				arguments,
				severity,
				sourceStart,
				sourceEnd);
		}
	}
	public void javadocMissingReference(int sourceStart, int sourceEnd, int modifiers){
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers))
			this.handle(IProblem.JavadocMissingSeeReference, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	public void javadocMissingReturnTag(int sourceStart, int sourceEnd, int modifiers){
		boolean overriding = (modifiers & (ExtraCompilerModifiers.AccImplementing|ExtraCompilerModifiers.AccOverriding)) != 0;
		boolean report = (this.options.getSeverity(CompilerOptions.MissingJavadocTags) != ProblemSeverities.Ignore)
						&& (!overriding || this.options.reportMissingJavadocTagsOverriding);
		if (report && javadocVisibility(this.options.reportMissingJavadocTagsVisibility, modifiers)) {
			this.handle(IProblem.JavadocMissingReturnTag, NoArgument, NoArgument, sourceStart, sourceEnd);
		}
	}
	public void javadocMissingTagDescription(char[] tokenName, int sourceStart, int sourceEnd, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocMissingTagDescription);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] { new String(tokenName) };
			// use IProblem.JavadocEmptyReturnTag for all identified tags
			this.handle(IProblem.JavadocEmptyReturnTag, arguments, arguments, sourceStart, sourceEnd);
		}
	}
	public void javadocMissingTagDescriptionAfterReference(int sourceStart, int sourceEnd, int modifiers){
		int severity = computeSeverity(IProblem.JavadocMissingTagDescription);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			this.handle(IProblem.JavadocMissingTagDescription, NoArgument, NoArgument, severity, sourceStart, sourceEnd);
		}
	}
	public void javadocMissingThrowsClassName(int sourceStart, int sourceEnd, int modifiers){
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			this.handle(IProblem.JavadocMissingThrowsClassName, NoArgument, NoArgument, sourceStart, sourceEnd);
		}
	}
	public void javadocMissingThrowsTag(TypeReference typeRef, int modifiers){
		int severity = computeSeverity(IProblem.JavadocMissingThrowsTag);
		if (severity == ProblemSeverities.Ignore) return;
		boolean overriding = (modifiers & (ExtraCompilerModifiers.AccImplementing|ExtraCompilerModifiers.AccOverriding)) != 0;
		boolean report = (this.options.getSeverity(CompilerOptions.MissingJavadocTags) != ProblemSeverities.Ignore)
						&& (!overriding || this.options.reportMissingJavadocTagsOverriding);
		if (report && javadocVisibility(this.options.reportMissingJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] { String.valueOf(typeRef.resolvedType.sourceName()) };
			this.handle(
				IProblem.JavadocMissingThrowsTag,
				arguments,
				arguments,
				severity,
				typeRef.sourceStart,
				typeRef.sourceEnd);
		}
	}
	public void javadocUndeclaredParamTagName(char[] token, int sourceStart, int sourceEnd, int modifiers) {
		int severity = computeSeverity(IProblem.JavadocInvalidParamName);
		if (severity == ProblemSeverities.Ignore) return;
		if (javadocVisibility(this.options.reportInvalidJavadocTagsVisibility, modifiers)) {
			String[] arguments = new String[] {String.valueOf(token)};
			this.handle(
				IProblem.JavadocInvalidParamName,
				arguments,
				arguments,
				severity,
				sourceStart,
				sourceEnd);
		}
	}
	
	public void javadocUnexpectedTag(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocUnexpectedTag, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	
	public void javadocUnexpectedText(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocUnexpectedText, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	
	public void javadocUnterminatedInlineTag(int sourceStart, int sourceEnd) {
		this.handle(IProblem.JavadocUnterminatedInlineTag, NoArgument, NoArgument, sourceStart, sourceEnd);
	}
	
	private boolean javadocVisibility(int visibility, int modifiers) {
		if (modifiers < 0) return true;
		switch (modifiers & ExtraCompilerModifiers.AccVisibilityMASK) {
			case ClassFileConstants.AccPublic :
				return true;
			case ClassFileConstants.AccProtected:
				return (visibility != ClassFileConstants.AccPublic);
			case ClassFileConstants.AccDefault:
				return (visibility == ClassFileConstants.AccDefault || visibility == ClassFileConstants.AccPrivate);
			case ClassFileConstants.AccPrivate:
				return (visibility == ClassFileConstants.AccPrivate);
		}
		return true;
	}
	
	private String javadocVisibilityArgument(int visibility, int modifiers) {
		String argument = null;
		switch (modifiers & ExtraCompilerModifiers.AccVisibilityMASK) {
			case ClassFileConstants.AccPublic :
				argument = CompilerOptions.PUBLIC;
				break;
			case ClassFileConstants.AccProtected:
				if (visibility != ClassFileConstants.AccPublic) {
					argument = CompilerOptions.PROTECTED;
				}
				break;
			case ClassFileConstants.AccDefault:
				if (visibility == ClassFileConstants.AccDefault || visibility == ClassFileConstants.AccPrivate) {
					argument = CompilerOptions.DEFAULT;
				}
				break;
			case ClassFileConstants.AccPrivate:
				if (visibility == ClassFileConstants.AccPrivate) {
					argument = CompilerOptions.PRIVATE;
				}
				break;
		}
		return argument;
	}
	
	public void localVariableHiding(LocalDeclaration local, Binding hiddenVariable, boolean  isSpecialArgHidingField) {
		if (hiddenVariable instanceof LocalVariableBinding) {
			int id = (local instanceof Argument)
					? IProblem.ArgumentHidingLocalVariable
					: IProblem.LocalVariableHidingLocalVariable;
			int severity = computeSeverity(id);
			if (severity == ProblemSeverities.Ignore) return;
			String[] arguments = new String[] {new String(local.name)  };
			this.handle(
				id,
				arguments,
				arguments,
				severity,
				nodeSourceStart(hiddenVariable, local),
				nodeSourceEnd(hiddenVariable, local));
		} else if (hiddenVariable instanceof FieldBinding) {
			if (isSpecialArgHidingField && !this.options.reportSpecialParameterHidingField){
				return;
			}
			int id = (local instanceof Argument)
					? IProblem.ArgumentHidingField
					: IProblem.LocalVariableHidingField;
			int severity = computeSeverity(id);
			if (severity == ProblemSeverities.Ignore) return;
			FieldBinding field = (FieldBinding) hiddenVariable;
			this.handle(
				id,
				new String[] {new String(local.name) , new String(field.declaringClass.readableName()) },
				new String[] {new String(local.name), new String(field.declaringClass.shortReadableName()) },
				severity,
				local.sourceStart,
				local.sourceEnd);
		}
	}
	
	public void localVariableNonNullComparedToNull(LocalVariableBinding local, ASTNode location) {
		int severity = computeSeverity(IProblem.NonNullLocalVariableComparisonYieldsFalse);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments;
		int problemId;
		if (local.isNonNull()) {
			char[][] annotationName = this.options.nonNullAnnotationName; // cannot be null if local is declared @NonNull
			arguments = new String[] {new String(local.name), new String(annotationName[annotationName.length-1])  };
			problemId = IProblem.SpecdNonNullLocalVariableComparisonYieldsFalse;
		} else {
			arguments = new String[] {new String(local.name)  };
			problemId = IProblem.NonNullLocalVariableComparisonYieldsFalse; 
		}
		this.handle(
			problemId,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	public void localVariableNullComparedToNonNull(LocalVariableBinding local, ASTNode location) {
		int severity = computeSeverity(IProblem.NullLocalVariableComparisonYieldsFalse);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(local.name)  };
		this.handle(
			IProblem.NullLocalVariableComparisonYieldsFalse,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	/**
	 * @param expr expression being compared for null or nonnull
	 * @param checkForNull true if checking for null, false if checking for nonnull 
	 */
	public boolean expressionNonNullComparison(Expression expr, boolean checkForNull) {
		int problemId = 0;
		Binding binding = null;
		String[] arguments = null;
		int start = 0, end = 0;
		Expression location = expr;
	
		if (expr.resolvedType != null) {
			long tagBits = expr.resolvedType.tagBits & TagBits.AnnotationNullMASK;
			if (tagBits == TagBits.AnnotationNonNull) {
				problemId = IProblem.RedundantNullCheckAgainstNonNullType;
				arguments = new String[] { String.valueOf(expr.resolvedType.nullAnnotatedReadableName(this.options, true)) };
				start = nodeSourceStart(location);
				end = nodeSourceEnd(location);
				handle(problemId, arguments, arguments, start, end);
				return true;
			}
		}
		// unwrap uninteresting nodes:
		while (true) {
			if (expr instanceof Assignment)
				return false; // don't report against the assignment, but the variable
			else if (expr instanceof CastExpression)
				expr = ((CastExpression) expr).expression;
			else
				break;
		}
		// check all those kinds of expressions that can possible answer NON_NULL from nullStatus():
		if (expr instanceof MessageSend) {
			problemId = checkForNull 
					? IProblem.NonNullMessageSendComparisonYieldsFalse
					: IProblem.RedundantNullCheckOnNonNullMessageSend;
			MethodBinding method = ((MessageSend)expr).binding;
			binding = method;
			arguments = new String[] { new String(method.shortReadableName()) };
			start = location.sourceStart;
			end = location.sourceEnd;
		} else if (expr instanceof Reference && !(expr instanceof ThisReference) && !(expr instanceof ArrayReference)) {
			FieldBinding field = ((Reference)expr).lastFieldBinding();
			if (field == null) {
				return false;
			}
			if (field.isNonNull()) {
				problemId = checkForNull
						? IProblem.NonNullSpecdFieldComparisonYieldsFalse
						: IProblem.RedundantNullCheckOnNonNullSpecdField;
				char[][] nonNullName = this.options.nonNullAnnotationName;
				arguments = new String[] { new String(field.name), 
										   new String(nonNullName[nonNullName.length-1]) };
			} else {
				// signaling redundancy based on syntactic analysis:
				problemId = checkForNull
						? IProblem.FieldComparisonYieldsFalse
						: IProblem.RedundantNullCheckOnField;
				arguments = new String[] { String.valueOf(field.name)};
			}
			binding = field;
			start = nodeSourceStart(binding, location);
			end = nodeSourceEnd(binding, location);
		} else if (expr instanceof AllocationExpression 
				|| expr instanceof ArrayAllocationExpression 
				|| expr instanceof ArrayInitializer
				|| expr instanceof ClassLiteralAccess
				|| expr instanceof ThisReference) {
			// fall through to bottom
		} else if (expr instanceof Literal
					|| expr instanceof ConditionalExpression) {
			if (expr instanceof NullLiteral) {
				needImplementation(location); // reported as nonnull??
				return false;
			}
			if (expr.resolvedType != null && expr.resolvedType.isBaseType()) {
				// false alarm, auto(un)boxing is involved
				return false;
			}
			// fall through to bottom
		} else if (expr instanceof BinaryExpression) {
			if ((expr.bits & ASTNode.ReturnTypeIDMASK) != TypeIds.T_JavaLangString) {
				// false alarm, primitive types involved, must be auto(un)boxing?
				return false;
			}
			// fall through to bottom
		} else {
			needImplementation(expr); // want to see if we get here
			return false;
		}
		if (problemId == 0) {
			// standard case, fill in details now
			problemId = checkForNull 
					? IProblem.NonNullExpressionComparisonYieldsFalse
					: IProblem.RedundantNullCheckOnNonNullExpression;
			start = location.sourceStart;
			end = location.sourceEnd;
			arguments = NoArgument;
		}
		this.handle(problemId, arguments, arguments, start, end);
		return true;
	}
	public void nullAnnotationUnsupportedLocation(Annotation annotation) {
		String[] arguments = new String[] {
			String.valueOf(annotation.resolvedType.readableName())
		};
		String[] shortArguments = new String[] {
			String.valueOf(annotation.resolvedType.shortReadableName())
		};
		handle(IProblem.NullAnnotationUnsupportedLocation,
			arguments, shortArguments, annotation.sourceStart, annotation.sourceEnd);
	}
	public void nullAnnotationUnsupportedLocation(TypeReference type) {
		int sourceEnd = type.sourceEnd;
		if (type instanceof ParameterizedSingleTypeReference) {
			ParameterizedSingleTypeReference typeReference = (ParameterizedSingleTypeReference) type;
			TypeReference[] typeArguments = typeReference.typeArguments;
			if (typeArguments[typeArguments.length - 1].sourceEnd > typeReference.sourceEnd) {
				sourceEnd = retrieveClosingAngleBracketPosition(typeReference.sourceEnd);
			} else {
				sourceEnd = type.sourceEnd;
			}
		} else if (type instanceof ParameterizedQualifiedTypeReference) {
			ParameterizedQualifiedTypeReference typeReference = (ParameterizedQualifiedTypeReference) type;
			sourceEnd = retrieveClosingAngleBracketPosition(typeReference.sourceEnd);
		} else {
			sourceEnd = type.sourceEnd;
		}
	
		handle(IProblem.NullAnnotationUnsupportedLocationAtType,
			NoArgument, NoArgument, type.sourceStart, sourceEnd);
	}
	public void localVariableNullInstanceof(LocalVariableBinding local, ASTNode location) {
		int severity = computeSeverity(IProblem.NullLocalVariableInstanceofYieldsFalse);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(local.name)  };
		this.handle(
			IProblem.NullLocalVariableInstanceofYieldsFalse,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	public void localVariableNullReference(LocalVariableBinding local, ASTNode location) {
		if (location instanceof Expression && (((Expression)location).implicitConversion & TypeIds.UNBOXING) != 0) {
			nullUnboxing(location, local.type);
			return;
		}
		int severity = computeSeverity(IProblem.NullLocalVariableReference);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(local.name)  };
		this.handle(
			IProblem.NullLocalVariableReference,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	public void localVariablePotentialNullReference(LocalVariableBinding local, ASTNode location) {
		if (location instanceof Expression && (((Expression)location).implicitConversion & TypeIds.UNBOXING) != 0) {
			potentialNullUnboxing(location, local.type);
			return;
		}
		if ((local.type.tagBits & TagBits.AnnotationNullable) != 0 && location instanceof Expression) {
			dereferencingNullableExpression((Expression) location);
			return;
		}
		int severity = computeSeverity(IProblem.PotentialNullLocalVariableReference);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(local.name)};
		this.handle(
			IProblem.PotentialNullLocalVariableReference,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void potentialNullUnboxing(ASTNode expression, TypeBinding boxType) {
		String[] arguments = new String[] { String.valueOf(boxType.readableName()) };
		String[] argumentsShort = new String[] { String.valueOf(boxType.shortReadableName()) };
		this.handle(IProblem.PotentialNullUnboxing, arguments, argumentsShort, expression.sourceStart, expression.sourceEnd);
	}
	public void nullUnboxing(ASTNode expression, TypeBinding boxType) {
		String[] arguments = new String[] { String.valueOf(boxType.readableName()) };
		String[] argumentsShort = new String[] { String.valueOf(boxType.shortReadableName()) };
		this.handle(IProblem.NullUnboxing, arguments, argumentsShort, expression.sourceStart, expression.sourceEnd);
	}
	public void nullableFieldDereference(FieldBinding variable, long position) {
		char[][] nullableName = this.options.nullableAnnotationName;
		String[] arguments = new String[] {new String(variable.name), new String(nullableName[nullableName.length-1])};
		this.handle(
			IProblem.NullableFieldReference,
			arguments,
			arguments,
			(int)(position >>> 32),
			(int)position);
	}
	
	public void localVariableRedundantCheckOnNonNull(LocalVariableBinding local, ASTNode location) {
		int severity = computeSeverity(IProblem.RedundantNullCheckOnNonNullLocalVariable);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments;
		int problemId;
		if (local.isNonNull()) {
			char[][] annotationName = this.options.nonNullAnnotationName; // cannot be null if local is declared @NonNull
			arguments = new String[] {new String(local.name), new String(annotationName[annotationName.length-1])  };
			problemId = IProblem.RedundantNullCheckOnSpecdNonNullLocalVariable;
		} else {
			arguments = new String[] {new String(local.name)  };
			problemId = IProblem.RedundantNullCheckOnNonNullLocalVariable; 
		}
		this.handle(
			problemId, 
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	public void localVariableRedundantCheckOnNull(LocalVariableBinding local, ASTNode location) {
		int severity = computeSeverity(IProblem.RedundantNullCheckOnNullLocalVariable);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(local.name)  };
		this.handle(
			IProblem.RedundantNullCheckOnNullLocalVariable,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	public void localVariableRedundantNullAssignment(LocalVariableBinding local, ASTNode location) {
		if ((location.bits & ASTNode.FirstAssignmentToLocal) != 0) // https://bugs.eclipse.org/338303 - Warning about Redundant assignment conflicts with definite assignment
			return;
		int severity = computeSeverity(IProblem.RedundantLocalVariableNullAssignment);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(local.name)  };
		this.handle(
			IProblem.RedundantLocalVariableNullAssignment,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	
	public void methodMustOverride(AbstractMethodDeclaration method, long complianceLevel) {
		MethodBinding binding = method.binding;
		this.handle(
			complianceLevel == ClassFileConstants.JDK1_5 ? IProblem.MethodMustOverride : IProblem.MethodMustOverrideOrImplement,
			new String[] {new String(binding.selector), typesAsString(binding, false), new String(binding.declaringClass.readableName()), },
			new String[] {new String(binding.selector), typesAsString(binding, true), new String(binding.declaringClass.shortReadableName()),},
			method.sourceStart,
			method.sourceEnd);
	}
	
	public void methodNameClash(MethodBinding currentMethod, MethodBinding inheritedMethod, int severity) {
		this.handle(
			IProblem.MethodNameClash,
			new String[] {
				new String(currentMethod.selector),
				typesAsString(currentMethod, false),
				new String(currentMethod.declaringClass.readableName()),
				typesAsString(inheritedMethod, false),
				new String(inheritedMethod.declaringClass.readableName()),
			 },
			new String[] {
				new String(currentMethod.selector),
				typesAsString(currentMethod, true),
				new String(currentMethod.declaringClass.shortReadableName()),
				typesAsString(inheritedMethod, true),
				new String(inheritedMethod.declaringClass.shortReadableName()),
			 },
			severity,
			currentMethod.sourceStart(),
			currentMethod.sourceEnd());
	}
	
	public void methodNameClashHidden(MethodBinding currentMethod, MethodBinding inheritedMethod) {
		this.handle(
			IProblem.MethodNameClashHidden,
			new String[] {
				new String(currentMethod.selector),
				typesAsString(currentMethod, currentMethod.parameters, false),
				new String(currentMethod.declaringClass.readableName()),
				typesAsString(inheritedMethod, inheritedMethod.parameters, false),
				new String(inheritedMethod.declaringClass.readableName()),
			 },
			new String[] {
				new String(currentMethod.selector),
				typesAsString(currentMethod, currentMethod.parameters, true),
				new String(currentMethod.declaringClass.shortReadableName()),
				typesAsString(inheritedMethod, inheritedMethod.parameters, true),
				new String(inheritedMethod.declaringClass.shortReadableName()),
			 },
			currentMethod.sourceStart(),
			currentMethod.sourceEnd());
	}
	
	public void methodNeedBody(AbstractMethodDeclaration methodDecl) {
		this.handle(
			IProblem.MethodRequiresBody,
			NoArgument,
			NoArgument,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void methodNeedingNoBody(MethodDeclaration methodDecl) {
		this.handle(
			((methodDecl.modifiers & ClassFileConstants.AccNative) != 0) ? IProblem.BodyForNativeMethod : IProblem.BodyForAbstractMethod,
			NoArgument,
			NoArgument,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void methodWithConstructorName(MethodDeclaration methodDecl) {
		this.handle(
			IProblem.MethodButWithConstructorName,
			NoArgument,
			NoArgument,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void methodCanBeDeclaredStatic(MethodDeclaration methodDecl) {
		int severity = computeSeverity(IProblem.MethodCanBeStatic);
		if (severity == ProblemSeverities.Ignore) return;
		MethodBinding method = methodDecl.binding;
		this.handle(
				IProblem.MethodCanBeStatic,
			new String[] {
				new String(method.declaringClass.readableName()),
				new String(method.selector),
				typesAsString(method, false)
			 },
			new String[] {
				new String(method.declaringClass.shortReadableName()),
				new String(method.selector),
				typesAsString(method, true)
			 },
			severity,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void methodCanBePotentiallyDeclaredStatic(MethodDeclaration methodDecl) {
		int severity = computeSeverity(IProblem.MethodCanBePotentiallyStatic);
		if (severity == ProblemSeverities.Ignore) return;
		MethodBinding method = methodDecl.binding;
		this.handle(
				IProblem.MethodCanBePotentiallyStatic,
			new String[] {
				new String(method.declaringClass.readableName()),
				new String(method.selector),
				typesAsString(method, false)
			 },
			new String[] {
				new String(method.declaringClass.shortReadableName()),
				new String(method.selector),
				typesAsString(method, true)
			 },
			severity,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	public void missingDeprecatedAnnotationForField(FieldDeclaration field) {
		int severity = computeSeverity(IProblem.FieldMissingDeprecatedAnnotation);
		if (severity == ProblemSeverities.Ignore) return;
		FieldBinding binding = field.binding;
		this.handle(
			IProblem.FieldMissingDeprecatedAnnotation,
			new String[] {new String(binding.declaringClass.readableName()), new String(binding.name), },
			new String[] {new String(binding.declaringClass.shortReadableName()), new String(binding.name), },
			severity,
			nodeSourceStart(binding, field),
			nodeSourceEnd(binding, field));
	}
	
	public void missingDeprecatedAnnotationForMethod(AbstractMethodDeclaration method) {
		int severity = computeSeverity(IProblem.MethodMissingDeprecatedAnnotation);
		if (severity == ProblemSeverities.Ignore) return;
		MethodBinding binding = method.binding;
		this.handle(
			IProblem.MethodMissingDeprecatedAnnotation,
			new String[] {new String(binding.selector), typesAsString(binding, false), new String(binding.declaringClass.readableName()), },
			new String[] {new String(binding.selector), typesAsString(binding, true), new String(binding.declaringClass.shortReadableName()),},
			severity,
			method.sourceStart,
			method.sourceEnd);
	}
	
	public void missingDeprecatedAnnotationForType(TypeDeclaration type) {
		int severity = computeSeverity(IProblem.TypeMissingDeprecatedAnnotation);
		if (severity == ProblemSeverities.Ignore) return;
		TypeBinding binding = type.binding;
		this.handle(
			IProblem.TypeMissingDeprecatedAnnotation,
			new String[] {new String(binding.readableName()), },
			new String[] {new String(binding.shortReadableName()),},
			severity,
			type.sourceStart,
			type.sourceEnd);
	}
	public void notAFunctionalInterface(TypeDeclaration type) {
		TypeBinding binding = type.binding;
		this.handle(
			IProblem.InterfaceNotFunctionalInterface,
			new String[] {new String(binding.readableName()), },
			new String[] {new String(binding.shortReadableName()),},
			type.sourceStart,
			type.sourceEnd);
	}
	public void missingEnumConstantCase(SwitchStatement switchStatement, FieldBinding enumConstant) {
		this.handle(
			switchStatement.defaultCase == null ? IProblem.MissingEnumConstantCase : IProblem.MissingEnumConstantCaseDespiteDefault,
			new String[] {new String(enumConstant.declaringClass.readableName()), new String(enumConstant.name) },
			new String[] {new String(enumConstant.declaringClass.shortReadableName()), new String(enumConstant.name) },
			switchStatement.expression.sourceStart,
			switchStatement.expression.sourceEnd);
	}
	public void missingDefaultCase(SwitchStatement switchStatement, boolean isEnumSwitch, TypeBinding expressionType) {
		if (isEnumSwitch) {
			this.handle(
					IProblem.MissingEnumDefaultCase,
					new String[] {new String(expressionType.readableName())},
					new String[] {new String(expressionType.shortReadableName())},
					switchStatement.expression.sourceStart,
					switchStatement.expression.sourceEnd);
		} else {
			this.handle(
					IProblem.MissingDefaultCase,
					NoArgument,
					NoArgument,
					switchStatement.expression.sourceStart,
					switchStatement.expression.sourceEnd);
		}
	}
	public void missingOverrideAnnotation(AbstractMethodDeclaration method) {
		int severity = computeSeverity(IProblem.MissingOverrideAnnotation);
		if (severity == ProblemSeverities.Ignore) return;
		MethodBinding binding = method.binding;
		this.handle(
			IProblem.MissingOverrideAnnotation,
			new String[] {new String(binding.selector), typesAsString(binding, false), new String(binding.declaringClass.readableName()), },
			new String[] {new String(binding.selector), typesAsString(binding, true), new String(binding.declaringClass.shortReadableName()),},
			severity,
			method.sourceStart,
			method.sourceEnd);
	}
	public void missingOverrideAnnotationForInterfaceMethodImplementation(AbstractMethodDeclaration method) {
		int severity = computeSeverity(IProblem.MissingOverrideAnnotationForInterfaceMethodImplementation);
		if (severity == ProblemSeverities.Ignore) return;
		MethodBinding binding = method.binding;
		this.handle(
			IProblem.MissingOverrideAnnotationForInterfaceMethodImplementation,
			new String[] {new String(binding.selector), typesAsString(binding, false), new String(binding.declaringClass.readableName()), },
			new String[] {new String(binding.selector), typesAsString(binding, true), new String(binding.declaringClass.shortReadableName()),},
			severity,
			method.sourceStart,
			method.sourceEnd);
	}
	public void missingReturnType(AbstractMethodDeclaration methodDecl) {
		this.handle(
			IProblem.MissingReturnType,
			NoArgument,
			NoArgument,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void missingSemiColon(Expression expression){
		this.handle(
			IProblem.MissingSemiColon,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void missingSerialVersion(TypeDeclaration typeDecl) {
		String[] arguments = new String[] {new String(typeDecl.name)};
		this.handle(
			IProblem.MissingSerialVersion,
			arguments,
			arguments,
			typeDecl.sourceStart,
			typeDecl.sourceEnd);
	}
	public void missingSynchronizedOnInheritedMethod(MethodBinding currentMethod, MethodBinding inheritedMethod) {
		this.handle(
				IProblem.MissingSynchronizedModifierInInheritedMethod,
				new String[] {
						new String(currentMethod.declaringClass.readableName()),
						new String(currentMethod.selector),
						typesAsString(currentMethod, false),
				},
				new String[] {
						new String(currentMethod.declaringClass.shortReadableName()),
						new String(currentMethod.selector),
						typesAsString(currentMethod, true),
				},
				currentMethod.sourceStart(),
				currentMethod.sourceEnd());
	}
	public void missingTypeInConstructor(ASTNode location, MethodBinding constructor) {
		List missingTypes = constructor.collectMissingTypes(null);
		if (missingTypes == null) {
			System.err.println("The constructor " + constructor + " is wrongly tagged as containing missing types"); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		TypeBinding missingType = (TypeBinding) missingTypes.get(0);
		int start = location.sourceStart;
		int end = location.sourceEnd;
		if (location instanceof QualifiedAllocationExpression) {
			QualifiedAllocationExpression qualifiedAllocation = (QualifiedAllocationExpression) location;
			if (qualifiedAllocation.anonymousType != null) {
				start = qualifiedAllocation.anonymousType.sourceStart;
				end = qualifiedAllocation.anonymousType.sourceEnd;
			}
		}
		this.handle(
				IProblem.MissingTypeInConstructor,
				new String[] {
				        new String(constructor.declaringClass.readableName()),
				        typesAsString(constructor, false),
				       	new String(missingType.readableName()),
				},
				new String[] {
				        new String(constructor.declaringClass.shortReadableName()),
				        typesAsString(constructor, true),
				       	new String(missingType.shortReadableName()),
				},
				start,
				end);
	}
	public void missingTypeInLambda(LambdaExpression lambda, MethodBinding method) {
		int nameSourceStart = lambda.sourceStart();
		int nameSourceEnd = lambda.diagnosticsSourceEnd();
		List missingTypes = method.collectMissingTypes(null);
		if (missingTypes == null) {
			System.err.println("The lambda expression " + method + " is wrongly tagged as containing missing types"); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		TypeBinding missingType = (TypeBinding) missingTypes.get(0);
		this.handle(
				IProblem.MissingTypeInLambda,
				new String[] {
				        new String(missingType.readableName()),
				},
				new String[] {
				        new String(missingType.shortReadableName()),
				},
				nameSourceStart,
				nameSourceEnd);
	}
	public void missingTypeInMethod(ASTNode astNode, MethodBinding method) {
		int nameSourceStart, nameSourceEnd;
		if (astNode instanceof MessageSend) {
			MessageSend messageSend = astNode instanceof MessageSend ? (MessageSend) (astNode) : null;
			nameSourceStart = (int) (messageSend.nameSourcePosition >>> 32);
			nameSourceEnd = (int) messageSend.nameSourcePosition;
		} else {
			nameSourceStart = astNode.sourceStart;
			nameSourceEnd = astNode.sourceEnd;
		}
		List missingTypes = method.collectMissingTypes(null);
		if (missingTypes == null) {
			System.err.println("The method " + method + " is wrongly tagged as containing missing types"); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		TypeBinding missingType = (TypeBinding) missingTypes.get(0);
		this.handle(
				IProblem.MissingTypeInMethod,
				new String[] {
				        new String(method.declaringClass.readableName()),
				        new String(method.selector),
				        typesAsString(method, false),
				       	new String(missingType.readableName()),
				},
				new String[] {
				        new String(method.declaringClass.shortReadableName()),
				        new String(method.selector),
				        typesAsString(method, true),
				       	new String(missingType.shortReadableName()),
				},
				nameSourceStart,
				nameSourceEnd);
	}
	public void missingValueForAnnotationMember(Annotation annotation, char[] memberName) {
		String memberString = new String(memberName);
		this.handle(
			IProblem.MissingValueForAnnotationMember,
			new String[] {new String(annotation.resolvedType.readableName()), memberString },
			new String[] {new String(annotation.resolvedType.shortReadableName()), memberString},
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	public void mustDefineDimensionsOrInitializer(ArrayAllocationExpression expression) {
		this.handle(
			IProblem.MustDefineEitherDimensionExpressionsOrInitializer,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void mustUseAStaticMethod(MessageSend messageSend, MethodBinding method) {
		this.handle(
			IProblem.StaticMethodRequested,
			new String[] {new String(method.declaringClass.readableName()), new String(method.selector), typesAsString(method, false)},
			new String[] {new String(method.declaringClass.shortReadableName()), new String(method.selector), typesAsString(method, true)},
			messageSend.sourceStart,
			messageSend.sourceEnd);
	}
	public void nativeMethodsCannotBeStrictfp(ReferenceBinding type, AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {new String(type.sourceName()), new String(methodDecl.selector)};
		this.handle(
			IProblem.NativeMethodsCannotBeStrictfp,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void needImplementation(ASTNode location) {
		this.abortDueToInternalError(Messages.abort_missingCode, location);
	}
	
	public void needToEmulateFieldAccess(FieldBinding field, ASTNode location, boolean isReadAccess) {
		int id = isReadAccess
				? IProblem.NeedToEmulateFieldReadAccess
				: IProblem.NeedToEmulateFieldWriteAccess;
		int severity = computeSeverity(id);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			id,
			new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
			new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
			severity,
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	public void needToEmulateMethodAccess(
		MethodBinding method,
		ASTNode location) {
	
		if (method.isConstructor()) {
			int severity = computeSeverity(IProblem.NeedToEmulateConstructorAccess);
			if (severity == ProblemSeverities.Ignore) return;
			if (method.declaringClass.isEnum())
				return; // tolerate emulation for enum constructors, which can only be made private
			this.handle(
				IProblem.NeedToEmulateConstructorAccess,
				new String[] {
					new String(method.declaringClass.readableName()),
					typesAsString(method, false)
				 },
				new String[] {
					new String(method.declaringClass.shortReadableName()),
					typesAsString(method, true)
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
			return;
		}
		int severity = computeSeverity(IProblem.NeedToEmulateMethodAccess);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.NeedToEmulateMethodAccess,
			new String[] {
				new String(method.declaringClass.readableName()),
				new String(method.selector),
				typesAsString(method, false)
			 },
			new String[] {
				new String(method.declaringClass.shortReadableName()),
				new String(method.selector),
				typesAsString(method, true)
			 },
			 severity,
			location.sourceStart,
			location.sourceEnd);
	}
	public void noAdditionalBoundAfterTypeVariable(TypeReference boundReference) {
		this.handle(
			IProblem.NoAdditionalBoundAfterTypeVariable,
			new String[] { new String(boundReference.resolvedType.readableName()) },
			new String[] { new String(boundReference.resolvedType.shortReadableName()) },
			boundReference.sourceStart,
			boundReference.sourceEnd);
	}
	private int nodeSourceEnd(ASTNode node) {
		if (node instanceof Reference) {
			Binding field = ((Reference) node).lastFieldBinding();
			if (field != null)
				return nodeSourceEnd(field, node);
		}
		return node.sourceEnd;
	}
	private int nodeSourceEnd(Binding field, ASTNode node) {
		return nodeSourceEnd(field, node, 0);
	}
	private int nodeSourceEnd(Binding field, ASTNode node, int index) {
		if (node instanceof ArrayTypeReference) {
			return ((ArrayTypeReference) node).originalSourceEnd;
		} else if (node instanceof QualifiedNameReference) {
			QualifiedNameReference ref = (QualifiedNameReference) node;
			if (ref.binding == field) {
				if (index == 0) {
					return (int) (ref.sourcePositions[ref.indexOfFirstFieldBinding-1]);
				} else {
					int length = ref.sourcePositions.length;
					if (index < length) {
						return (int) (ref.sourcePositions[index]);
					}
					return (int) (ref.sourcePositions[0]);
				}
			}
			FieldBinding[] otherFields = ref.otherBindings;
			if (otherFields != null) {
				int offset = ref.indexOfFirstFieldBinding;
				if (index != 0) {
					for (int i = 0, length = otherFields.length; i < length; i++) {
						if ((otherFields[i] == field) && (i + offset == index)) {
							return (int) (ref.sourcePositions[i + offset]);
						}
					}
				} else {
					for (int i = 0, length = otherFields.length; i < length; i++) {
						if (otherFields[i] == field)
							return (int) (ref.sourcePositions[i + offset]);
					}
				}
			}
		} else if (node instanceof ParameterizedQualifiedTypeReference) {
			ParameterizedQualifiedTypeReference reference = (ParameterizedQualifiedTypeReference) node;
			if (index < reference.sourcePositions.length) {
				return (int) reference.sourcePositions[index];
			}
		} else if (node instanceof ArrayQualifiedTypeReference) {
			ArrayQualifiedTypeReference reference = (ArrayQualifiedTypeReference) node;
			int length = reference.sourcePositions.length;
			if (index < length) {
				return (int) reference.sourcePositions[index];
			}
			return (int) reference.sourcePositions[length - 1];
		} else if (node instanceof QualifiedTypeReference) {
			QualifiedTypeReference reference = (QualifiedTypeReference) node;
			int length = reference.sourcePositions.length;
			if (index < length) {
				return (int) reference.sourcePositions[index];
			}
		}
		return node.sourceEnd;
	}
	private int nodeSourceStart(ASTNode node) {
		if (node instanceof Reference) {
			Binding field = ((Reference) node).lastFieldBinding();
			if (field != null)
				return nodeSourceStart(field, node);
		}
		return node.sourceStart;
	}
	private int nodeSourceStart(Binding field, ASTNode node) {
		return nodeSourceStart(field, node, 0);
	}
	private int nodeSourceStart(Binding field, ASTNode node, int index) {
		if (node instanceof FieldReference) {
			FieldReference fieldReference = (FieldReference) node;
			return (int) (fieldReference.nameSourcePosition >> 32);
		} else 	if (node instanceof QualifiedNameReference) {
			QualifiedNameReference ref = (QualifiedNameReference) node;
			if (ref.binding == field) {
				if (index == 0) {
					return (int) (ref.sourcePositions[ref.indexOfFirstFieldBinding-1] >> 32);
				} else {
					return (int) (ref.sourcePositions[index] >> 32);
				}
			}
			FieldBinding[] otherFields = ref.otherBindings;
			if (otherFields != null) {
				int offset = ref.indexOfFirstFieldBinding;
				if (index != 0) {
					for (int i = 0, length = otherFields.length; i < length; i++) {
						if ((otherFields[i] == field) && (i + offset == index)) {
							return (int) (ref.sourcePositions[i + offset] >> 32);
						}
					}
				} else {
					for (int i = 0, length = otherFields.length; i < length; i++) {
						if (otherFields[i] == field) {
							return (int) (ref.sourcePositions[i + offset] >> 32);
						}
					}
				}
			}
		} else if (node instanceof ParameterizedQualifiedTypeReference) {
			ParameterizedQualifiedTypeReference reference = (ParameterizedQualifiedTypeReference) node;
			return (int) (reference.sourcePositions[0]>>>32);
		}
		return node.sourceStart;
	}
	public void noMoreAvailableSpaceForArgument(LocalVariableBinding local, ASTNode location) {
		String[] arguments = new String[]{ new String(local.name) };
		this.handle(
			local instanceof SyntheticArgumentBinding
				? IProblem.TooManySyntheticArgumentSlots
				: IProblem.TooManyArgumentSlots,
			arguments,
			arguments,
			ProblemSeverities.Abort | ProblemSeverities.Error | ProblemSeverities.Fatal,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void noMoreAvailableSpaceForConstant(TypeDeclaration typeDeclaration) {
		this.handle(
			IProblem.TooManyBytesForStringConstant,
			new String[]{ new String(typeDeclaration.binding.readableName())},
			new String[]{ new String(typeDeclaration.binding.shortReadableName())},
			ProblemSeverities.Abort | ProblemSeverities.Error | ProblemSeverities.Fatal,
			typeDeclaration.sourceStart,
			typeDeclaration.sourceEnd);
	}
	
	public void noMoreAvailableSpaceForLocal(LocalVariableBinding local, ASTNode location) {
		String[] arguments = new String[]{ new String(local.name) };
		this.handle(
			IProblem.TooManyLocalVariableSlots,
			arguments,
			arguments,
			ProblemSeverities.Abort | ProblemSeverities.Error | ProblemSeverities.Fatal,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location));
	}
	public void noMoreAvailableSpaceInConstantPool(TypeDeclaration typeDeclaration) {
		this.handle(
			IProblem.TooManyConstantsInConstantPool,
			new String[]{ new String(typeDeclaration.binding.readableName())},
			new String[]{ new String(typeDeclaration.binding.shortReadableName())},
			ProblemSeverities.Abort | ProblemSeverities.Error | ProblemSeverities.Fatal,
			typeDeclaration.sourceStart,
			typeDeclaration.sourceEnd);
	}
	
	public void nonExternalizedStringLiteral(ASTNode location) {
		this.handle(
			IProblem.NonExternalizedStringLiteral,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	
	public void nonGenericTypeCannotBeParameterized(int index, ASTNode location, TypeBinding type, TypeBinding[] argumentTypes) {
		if (location == null) { // binary case
		    this.handle(
				IProblem.NonGenericType,
				new String[] {new String(type.readableName()), typesAsString(argumentTypes, false)},
				new String[] {new String(type.shortReadableName()), typesAsString(argumentTypes, true)},
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
		    return;
		}
	    this.handle(
			IProblem.NonGenericType,
			new String[] {new String(type.readableName()), typesAsString(argumentTypes, false)},
			new String[] {new String(type.shortReadableName()), typesAsString(argumentTypes, true)},
			nodeSourceStart(null, location),
			nodeSourceEnd(null, location, index));
	}
	public void nonStaticAccessToStaticField(ASTNode location, FieldBinding field) {
		nonStaticAccessToStaticField(location, field, -1);
	}
	public void nonStaticAccessToStaticField(ASTNode location, FieldBinding field, int index) {
		int severity = computeSeverity(IProblem.NonStaticAccessToStaticField);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.NonStaticAccessToStaticField,
			new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
			new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
			severity,
			nodeSourceStart(field, location, index),
			nodeSourceEnd(field, location, index));
	}
	public void nonStaticAccessToStaticMethod(ASTNode location, MethodBinding method) {
		this.handle(
			IProblem.NonStaticAccessToStaticMethod,
			new String[] {new String(method.declaringClass.readableName()), new String(method.selector), typesAsString(method, false)},
			new String[] {new String(method.declaringClass.shortReadableName()), new String(method.selector), typesAsString(method, true)},
			location.sourceStart,
			location.sourceEnd);
	}
	public void nonStaticContextForEnumMemberType(SourceTypeBinding type) {
		String[] arguments = new String[] {new String(type.sourceName())};
		this.handle(
			IProblem.NonStaticContextForEnumMemberType,
			arguments,
			arguments,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void noSuchEnclosingInstance(TypeBinding targetType, ASTNode location, boolean isConstructorCall) {
	
		int id;
	
		if (isConstructorCall) {
			//28 = No enclosing instance of type {0} is available due to some intermediate constructor invocation
			id = IProblem.EnclosingInstanceInConstructorCall;
		} else if ((location instanceof ExplicitConstructorCall)
					&& ((ExplicitConstructorCall) location).accessMode == ExplicitConstructorCall.ImplicitSuper) {
			//20 = No enclosing instance of type {0} is accessible to invoke the super constructor. Must define a constructor and explicitly qualify its super constructor invocation with an instance of {0} (e.g. x.super() where x is an instance of {0}).
			id = IProblem.MissingEnclosingInstanceForConstructorCall;
		} else if (location instanceof AllocationExpression
					&& (((AllocationExpression) location).binding.declaringClass.isMemberType()
						|| (((AllocationExpression) location).binding.declaringClass.isAnonymousType()
							&& ((AllocationExpression) location).binding.declaringClass.superclass().isMemberType()))) {
			//21 = No enclosing instance of type {0} is accessible. Must qualify the allocation with an enclosing instance of type {0} (e.g. x.new A() where x is an instance of {0}).
			id = IProblem.MissingEnclosingInstance;
		} else { // default
			//22 = No enclosing instance of the type {0} is accessible in scope
			id = IProblem.IncorrectEnclosingInstanceReference;
		}
	
		this.handle(
			id,
			new String[] { new String(targetType.readableName())},
			new String[] { new String(targetType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void notCompatibleTypesError(EqualExpression expression, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.IncompatibleTypesInEqualityOperator,
			new String[] {leftName, rightName },
			new String[] {leftShortName, rightShortName },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void notCompatibleTypesError(InstanceOfExpression expression, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.IncompatibleTypesInConditionalOperator,
			new String[] {leftName, rightName },
			new String[] {leftShortName, rightShortName },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void notCompatibleTypesErrorInForeach(Expression expression, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.IncompatibleTypesInForeach,
			new String[] {leftName, rightName },
			new String[] {leftShortName, rightShortName },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void objectCannotBeGeneric(TypeDeclaration typeDecl) {
		this.handle(
			IProblem.ObjectCannotBeGeneric,
			NoArgument,
			NoArgument,
			typeDecl.typeParameters[0].sourceStart,
			typeDecl.typeParameters[typeDecl.typeParameters.length-1].sourceEnd);
	}
	public void objectCannotHaveSuperTypes(SourceTypeBinding type) {
		this.handle(
			IProblem.ObjectCannotHaveSuperTypes,
			NoArgument,
			NoArgument,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void objectMustBeClass(SourceTypeBinding type) {
		this.handle(
			IProblem.ObjectMustBeClass,
			NoArgument,
			NoArgument,
			type.sourceStart(),
			type.sourceEnd());
	}
	public void operatorOnlyValidOnNumericType(CompoundAssignment  assignment, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.TypeMismatch,
			new String[] {leftName, rightName },
			new String[] {leftShortName, rightShortName },
			assignment.sourceStart,
			assignment.sourceEnd);
	}
	public void overridesDeprecatedMethod(MethodBinding localMethod, MethodBinding inheritedMethod) {
		this.handle(
			IProblem.OverridingDeprecatedMethod,
			new String[] {
				new String(
						CharOperation.concat(
							localMethod.declaringClass.readableName(),
							localMethod.readableName(),
							'.')),
				new String(inheritedMethod.declaringClass.readableName())},
			new String[] {
				new String(
						CharOperation.concat(
							localMethod.declaringClass.shortReadableName(),
							localMethod.shortReadableName(),
							'.')),
				new String(inheritedMethod.declaringClass.shortReadableName())},
			localMethod.sourceStart(),
			localMethod.sourceEnd());
	}
	public void overridesMethodWithoutSuperInvocation(MethodBinding localMethod) {
		this.handle(
			IProblem.OverridingMethodWithoutSuperInvocation,
			new String[] {
				new String(
						CharOperation.concat(
							localMethod.declaringClass.readableName(),
							localMethod.readableName(),
							'.'))
				},
			new String[] {
				new String(
						CharOperation.concat(
							localMethod.declaringClass.shortReadableName(),
							localMethod.shortReadableName(),
							'.'))
				},
			localMethod.sourceStart(),
			localMethod.sourceEnd());
	}
	public void overridesPackageDefaultMethod(MethodBinding localMethod, MethodBinding inheritedMethod) {
		this.handle(
			IProblem.OverridingNonVisibleMethod,
			new String[] {
				new String(
						CharOperation.concat(
							localMethod.declaringClass.readableName(),
							localMethod.readableName(),
							'.')),
				new String(inheritedMethod.declaringClass.readableName())},
			new String[] {
				new String(
						CharOperation.concat(
							localMethod.declaringClass.shortReadableName(),
							localMethod.shortReadableName(),
							'.')),
				new String(inheritedMethod.declaringClass.shortReadableName())},
			localMethod.sourceStart(),
			localMethod.sourceEnd());
	}
	public void packageCollidesWithType(CompilationUnitDeclaration compUnitDecl) {
		String[] arguments = new String[] {CharOperation.toString(compUnitDecl.currentPackage.tokens)};
		this.handle(
			IProblem.PackageCollidesWithType,
			arguments,
			arguments,
			compUnitDecl.currentPackage.sourceStart,
			compUnitDecl.currentPackage.sourceEnd);
	}
	public void packageIsNotExpectedPackage(CompilationUnitDeclaration compUnitDecl) {
		boolean hasPackageDeclaration = compUnitDecl.currentPackage == null;
		String[] arguments = new String[] {
			CharOperation.toString(compUnitDecl.compilationResult.compilationUnit.getPackageName()),
			hasPackageDeclaration ? "" : CharOperation.toString(compUnitDecl.currentPackage.tokens), //$NON-NLS-1$
		};
		int end;
		if (compUnitDecl.sourceEnd <= 0) {
			end = -1;
		} else {
			end = hasPackageDeclaration ? 0 : compUnitDecl.currentPackage.sourceEnd;
		}	
		this.handle(
			IProblem.PackageIsNotExpectedPackage,
			arguments,
			arguments,
			hasPackageDeclaration ? 0 : compUnitDecl.currentPackage.sourceStart,
			end);
	}
	public void parameterAssignment(LocalVariableBinding local, ASTNode location) {
		int severity = computeSeverity(IProblem.ParameterAssignment);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] { new String(local.readableName())};
		this.handle(
			IProblem.ParameterAssignment,
			arguments,
			arguments,
			severity,
			nodeSourceStart(local, location),
			nodeSourceEnd(local, location)); // should never be a qualified name reference
	}
	private String parameterBoundAsString(TypeVariableBinding typeVariable, boolean makeShort) {
	    StringBuffer nameBuffer = new StringBuffer(10);
	    if (TypeBinding.equalsEquals(typeVariable.firstBound, typeVariable.superclass)) {
	        nameBuffer.append(makeShort ? typeVariable.superclass.shortReadableName() : typeVariable.superclass.readableName());
	    }
	    int length;
	    if ((length = typeVariable.superInterfaces.length) > 0) {
		    for (int i = 0; i < length; i++) {
		        if (i > 0 || TypeBinding.equalsEquals(typeVariable.firstBound, typeVariable.superclass)) nameBuffer.append(" & "); //$NON-NLS-1$
		        nameBuffer.append(makeShort ? typeVariable.superInterfaces[i].shortReadableName() : typeVariable.superInterfaces[i].readableName());
		    }
		}
		return nameBuffer.toString();
	}
	public void parameterizedMemberTypeMissingArguments(ASTNode location, TypeBinding type, int index) {
		if (location == null) { // binary case
		    this.handle(
				IProblem.MissingArgumentsForParameterizedMemberType,
				new String[] {new String(type.readableName())},
				new String[] {new String(type.shortReadableName())},
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
		    return;
		}
	    this.handle(
			IProblem.MissingArgumentsForParameterizedMemberType,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			location.sourceStart,
			nodeSourceEnd(null, location, index));
	}
	public void parseError(
		int startPosition,
		int endPosition,
		int currentToken,
		char[] currentTokenSource,
		String errorTokenName,
		String[] possibleTokens) {
	
		if (possibleTokens.length == 0) { //no suggestion available
			if (isKeyword(currentToken)) {
				String[] arguments = new String[] {new String(currentTokenSource)};
				this.handle(
					IProblem.ParsingErrorOnKeywordNoSuggestion,
					arguments,
					arguments,
					// this is the current -invalid- token position
					startPosition,
					endPosition);
				return;
			} else {
				String[] arguments = new String[] {errorTokenName};
				this.handle(
					IProblem.ParsingErrorNoSuggestion,
					arguments,
					arguments,
					// this is the current -invalid- token position
					startPosition,
					endPosition);
				return;
			}
		}
	
		//build a list of probable right tokens
		StringBuffer list = new StringBuffer(20);
		for (int i = 0, max = possibleTokens.length; i < max; i++) {
			if (i > 0)
				list.append(", "); //$NON-NLS-1$
			list.append('"');
			list.append(possibleTokens[i]);
			list.append('"');
		}
	
		if (isKeyword(currentToken)) {
			String[] arguments = new String[] {new String(currentTokenSource), list.toString()};
			this.handle(
				IProblem.ParsingErrorOnKeyword,
				arguments,
				arguments,
				// this is the current -invalid- token position
				startPosition,
				endPosition);
			return;
		}
		//extract the literal when it's a literal
		if (isLiteral(currentToken) ||
			isIdentifier(currentToken)) {
				errorTokenName = new String(currentTokenSource);
		}
	
		String[] arguments = new String[] {errorTokenName, list.toString()};
		this.handle(
			IProblem.ParsingError,
			arguments,
			arguments,
			// this is the current -invalid- token position
			startPosition,
			endPosition);
	}
	public void parseErrorDeleteToken(
		int start,
		int end,
		int currentKind,
		char[] errorTokenSource,
		String errorTokenName){
		syntaxError(
			IProblem.ParsingErrorDeleteToken,
			start,
			end,
			currentKind,
			errorTokenSource,
			errorTokenName,
			null);
	}
	
	public void parseErrorDeleteTokens(
		int start,
		int end){
		this.handle(
			IProblem.ParsingErrorDeleteTokens,
			NoArgument,
			NoArgument,
			start,
			end);
	}
	public void parseErrorInsertAfterToken(
		int start,
		int end,
		int currentKind,
		char[] errorTokenSource,
		String errorTokenName,
		String expectedToken){
		syntaxError(
			IProblem.ParsingErrorInsertTokenAfter,
			start,
			end,
			currentKind,
			errorTokenSource,
			errorTokenName,
			expectedToken);
	}
	public void parseErrorInsertBeforeToken(
		int start,
		int end,
		int currentKind,
		char[] errorTokenSource,
		String errorTokenName,
		String expectedToken){
		syntaxError(
			IProblem.ParsingErrorInsertTokenBefore,
			start,
			end,
			currentKind,
			errorTokenSource,
			errorTokenName,
			expectedToken);
	}
	public void parseErrorInsertToComplete(
		int start,
		int end,
		String inserted,
		String completed){
		String[] arguments = new String[] {inserted, completed};
		this.handle(
			IProblem.ParsingErrorInsertToComplete,
			arguments,
			arguments,
			start,
			end);
	}
	
	public void parseErrorInsertToCompletePhrase(
		int start,
		int end,
		String inserted){
		String[] arguments = new String[] {inserted};
		this.handle(
			IProblem.ParsingErrorInsertToCompletePhrase,
			arguments,
			arguments,
			start,
			end);
	}
	public void parseErrorInsertToCompleteScope(
		int start,
		int end,
		String inserted){
		String[] arguments = new String[] {inserted};
		this.handle(
			IProblem.ParsingErrorInsertToCompleteScope,
			arguments,
			arguments,
			start,
			end);
	}
	public void parseErrorInvalidToken(
		int start,
		int end,
		int currentKind,
		char[] errorTokenSource,
		String errorTokenName,
		String expectedToken){
		syntaxError(
			IProblem.ParsingErrorInvalidToken,
			start,
			end,
			currentKind,
			errorTokenSource,
			errorTokenName,
			expectedToken);
	}
	public void parseErrorMergeTokens(
		int start,
		int end,
		String expectedToken){
		String[] arguments = new String[] {expectedToken};
		this.handle(
			IProblem.ParsingErrorMergeTokens,
			arguments,
			arguments,
			start,
			end);
	}
	public void parseErrorMisplacedConstruct(
		int start,
		int end){
		this.handle(
			IProblem.ParsingErrorMisplacedConstruct,
			NoArgument,
			NoArgument,
			start,
			end);
	}
	public void parseErrorNoSuggestion(
		int start,
		int end,
		int currentKind,
		char[] errorTokenSource,
		String errorTokenName){
		syntaxError(
			IProblem.ParsingErrorNoSuggestion,
			start,
			end,
			currentKind,
			errorTokenSource,
			errorTokenName,
			null);
	}
	public void parseErrorNoSuggestionForTokens(
		int start,
		int end){
		this.handle(
			IProblem.ParsingErrorNoSuggestionForTokens,
			NoArgument,
			NoArgument,
			start,
			end);
	}
	public void parseErrorReplaceToken(
		int start,
		int end,
		int currentKind,
		char[] errorTokenSource,
		String errorTokenName,
		String expectedToken){
		syntaxError(
			IProblem.ParsingError,
			start,
			end,
			currentKind,
			errorTokenSource,
			errorTokenName,
			expectedToken);
	}
	public void parseErrorReplaceTokens(
		int start,
		int end,
		String expectedToken){
		String[] arguments = new String[] {expectedToken};
		this.handle(
			IProblem.ParsingErrorReplaceTokens,
			arguments,
			arguments,
			start,
			end);
	}
	public void parseErrorUnexpectedEnd(
		int start,
		int end){
	
		String[] arguments;
		if(this.referenceContext instanceof ConstructorDeclaration) {
			arguments = new String[] {Messages.parser_endOfConstructor};
		} else if(this.referenceContext instanceof MethodDeclaration) {
			arguments = new String[] {Messages.parser_endOfMethod};
		} else if(this.referenceContext instanceof TypeDeclaration) {
			arguments = new String[] {Messages.parser_endOfInitializer};
		} else {
			arguments = new String[] {Messages.parser_endOfFile};
		}
		this.handle(
			IProblem.ParsingErrorUnexpectedEOF,
			arguments,
			arguments,
			start,
			end);
	}
	public void possibleAccidentalBooleanAssignment(Assignment assignment) {
		this.handle(
			IProblem.PossibleAccidentalBooleanAssignment,
			NoArgument,
			NoArgument,
			assignment.sourceStart,
			assignment.sourceEnd);
	}
	public void possibleFallThroughCase(CaseStatement caseStatement) {
		// as long as we consider fake reachable as reachable, better keep 'possible' in the name
		this.handle(
			IProblem.FallthroughCase,
			NoArgument,
			NoArgument,
			caseStatement.sourceStart,
			caseStatement.sourceEnd);
	}
	public void publicClassMustMatchFileName(CompilationUnitDeclaration compUnitDecl, TypeDeclaration typeDecl) {
		this.referenceContext = typeDecl; // report the problem against the type not the entire compilation unit
		String[] arguments = new String[] {new String(compUnitDecl.getFileName()), new String(typeDecl.name)};
		this.handle(
			IProblem.PublicClassMustMatchFileName,
			arguments,
			arguments,
			typeDecl.sourceStart,
			typeDecl.sourceEnd,
			compUnitDecl.compilationResult);
	}
	public void rawMemberTypeCannotBeParameterized(ASTNode location, ReferenceBinding type, TypeBinding[] argumentTypes) {
		if (location == null) { // binary case
		    this.handle(
				IProblem.RawMemberTypeCannotBeParameterized,
				new String[] {new String(type.readableName()), typesAsString(argumentTypes, false), new String(type.enclosingType().readableName())},
				new String[] {new String(type.shortReadableName()), typesAsString(argumentTypes, true), new String(type.enclosingType().shortReadableName())},
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
		    return;
		}
	    this.handle(
			IProblem.RawMemberTypeCannotBeParameterized,
			new String[] {new String(type.readableName()), typesAsString(argumentTypes, false), new String(type.enclosingType().readableName())},
			new String[] {new String(type.shortReadableName()), typesAsString(argumentTypes, true), new String(type.enclosingType().shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void rawTypeReference(ASTNode location, TypeBinding type) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		type = type.leafComponentType();
	    this.handle(
			IProblem.RawTypeReference,
			new String[] {new String(type.readableName()), new String(type.erasure().readableName()), },
			new String[] {new String(type.shortReadableName()),new String(type.erasure().shortReadableName()),},
			location.sourceStart,
			nodeSourceEnd(null, location, Integer.MAX_VALUE));
	}
	public void recursiveConstructorInvocation(ExplicitConstructorCall constructorCall) {
		this.handle(
			IProblem.RecursiveConstructorInvocation,
			new String[] {
				new String(constructorCall.binding.declaringClass.readableName()),
				typesAsString(constructorCall.binding, false)
			},
			new String[] {
				new String(constructorCall.binding.declaringClass.shortReadableName()),
				typesAsString(constructorCall.binding, true)
			},
			constructorCall.sourceStart,
			constructorCall.sourceEnd);
	}
	public void redefineArgument(Argument arg) {
		String[] arguments = new String[] {new String(arg.name)};
		this.handle(
			IProblem.RedefinedArgument,
			arguments,
			arguments,
			arg.sourceStart,
			arg.sourceEnd);
	}
	public void redefineLocal(LocalDeclaration localDecl) {
		String[] arguments = new String[] {new String(localDecl.name)};
		this.handle(
			IProblem.RedefinedLocal,
			arguments,
			arguments,
			localDecl.sourceStart,
			localDecl.sourceEnd);
	}
	public void redundantSuperInterface(SourceTypeBinding type, TypeReference reference, ReferenceBinding superinterface, ReferenceBinding declaringType) {
		int severity = computeSeverity(IProblem.RedundantSuperinterface);
		if (severity != ProblemSeverities.Ignore) {
			this.handle(
				IProblem.RedundantSuperinterface,
				new String[] {
					new String(superinterface.readableName()),
					new String(type.readableName()),
					new String(declaringType.readableName())},
				new String[] {
					new String(superinterface.shortReadableName()),
					new String(type.shortReadableName()),
					new String(declaringType.shortReadableName())},
				severity,
				reference.sourceStart,
				reference.sourceEnd);
		}
	}
	public void referenceMustBeArrayTypeAt(TypeBinding arrayType, ArrayReference arrayRef) {
		this.handle(
			IProblem.ArrayReferenceRequired,
			new String[] {new String(arrayType.readableName())},
			new String[] {new String(arrayType.shortReadableName())},
			arrayRef.sourceStart,
			arrayRef.sourceEnd);
	}
	public void repeatedAnnotationWithContainer(Annotation annotation, Annotation container) {
		this.handle(
			IProblem.RepeatedAnnotationWithContainerAnnotation,
			new String[] {new String(annotation.resolvedType.readableName()), new String(container.resolvedType.readableName())},
			new String[] {new String(annotation.resolvedType.shortReadableName()), new String(container.resolvedType.shortReadableName())},
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	public void containerAnnotationTypeMustHaveValue(ASTNode markerNode, ReferenceBinding containerAnnotationType) {
		this.handle(
			IProblem.ContainerAnnotationTypeMustHaveValue,
			new String[] {new String(containerAnnotationType.readableName())},
			new String[] {new String(containerAnnotationType.shortReadableName())},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	public void containerAnnotationTypeHasWrongValueType(ASTNode markerNode, ReferenceBinding containerAnnotationType, ReferenceBinding annotationType, TypeBinding returnType) {
		this.handle(
			IProblem.ContainerAnnotationTypeHasWrongValueType,
			new String[] {new String(containerAnnotationType.readableName()), new String(annotationType.readableName()), new String(returnType.readableName())},
			new String[] {new String(containerAnnotationType.shortReadableName()), new String(annotationType.shortReadableName()), new String(returnType.shortReadableName())},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	public void containerAnnotationTypeHasNonDefaultMembers(ASTNode markerNode, ReferenceBinding containerAnnotationType, char[] selector) {
		this.handle(
			IProblem.ContainerAnnotationTypeHasNonDefaultMembers,
			new String[] {new String(containerAnnotationType.readableName()), new String(selector)},
			new String[] {new String(containerAnnotationType.shortReadableName()), new String(selector)},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	public void containerAnnotationTypeHasShorterRetention(ASTNode markerNode, ReferenceBinding annotationType, String annotationRetention, ReferenceBinding containerAnnotationType, String containerRetention) {
		this.handle(
			IProblem.ContainerAnnotationTypeHasShorterRetention,
			new String[] {new String(annotationType.readableName()), annotationRetention, new String(containerAnnotationType.readableName()), containerRetention},
			new String[] {new String(annotationType.shortReadableName()), annotationRetention, new String(containerAnnotationType.shortReadableName()), containerRetention},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	public void repeatableAnnotationTypeTargetMismatch(ASTNode markerNode, ReferenceBinding annotationType, ReferenceBinding containerAnnotationType, String unmetTargets) {
		this.handle(
			IProblem.RepeatableAnnotationTypeTargetMismatch,
			new String[] {new String(annotationType.readableName()), new String(containerAnnotationType.readableName()), unmetTargets},
			new String[] {new String(annotationType.shortReadableName()), new String(containerAnnotationType.shortReadableName()), unmetTargets},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	
	public void repeatableAnnotationTypeIsDocumented(ASTNode markerNode, ReferenceBinding annotationType, ReferenceBinding containerAnnotationType) {
		this.handle(
			IProblem.RepeatableAnnotationTypeIsDocumented,
			new String[] {new String(annotationType.readableName()), new String(containerAnnotationType.readableName())},
			new String[] {new String(annotationType.shortReadableName()), new String(containerAnnotationType.shortReadableName())},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	
	public void repeatableAnnotationTypeIsInherited(ASTNode markerNode, ReferenceBinding annotationType, ReferenceBinding containerAnnotationType) {
		this.handle(
			IProblem.RepeatableAnnotationTypeIsInherited,
			new String[] {new String(annotationType.readableName()), new String(containerAnnotationType.readableName())},
			new String[] {new String(annotationType.shortReadableName()), new String(containerAnnotationType.shortReadableName())},
			markerNode.sourceStart,
			markerNode.sourceEnd);
	}
	
	public void repeatableAnnotationWithRepeatingContainer(Annotation annotation, ReferenceBinding containerType) {
		this.handle(
			IProblem.RepeatableAnnotationWithRepeatingContainerAnnotation,
			new String[] {new String(annotation.resolvedType.readableName()), new String(containerType.readableName())},
			new String[] {new String(annotation.resolvedType.shortReadableName()), new String(containerType.shortReadableName())},
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	
	public void reset() {
		this.positionScanner = null;
	}
	public void resourceHasToImplementAutoCloseable(TypeBinding binding, TypeReference typeReference) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_7) {
			return; // Not supported in 1.7 would have been reported. Hence another not required
		}
		this.handle(
				IProblem.ResourceHasToImplementAutoCloseable,
				new String[] {new String(binding.readableName())},
				new String[] {new String(binding.shortReadableName())},
				typeReference.sourceStart,
				typeReference.sourceEnd);
	}
	private int retrieveClosingAngleBracketPosition(int start) {
		if (this.referenceContext == null) return start;
		CompilationResult compilationResult = this.referenceContext.compilationResult();
		if (compilationResult == null) return start;
		ICompilationUnit compilationUnit = compilationResult.getCompilationUnit();
		if (compilationUnit == null) return start;
		char[] contents = compilationUnit.getContents();
		if (contents.length == 0) return start;
		if (this.positionScanner == null) {
			this.positionScanner = new Scanner(false, false, false, this.options.sourceLevel, this.options.complianceLevel, null, null, false);
			this.positionScanner.returnOnlyGreater = true;
		}
		this.positionScanner.setSource(contents);
		this.positionScanner.resetTo(start, contents.length);
		int end = start;
		int count = 0;
		try {
			int token;
			loop: while ((token = this.positionScanner.getNextToken()) != TerminalTokens.TokenNameEOF) {
				switch(token) {
					case TerminalTokens.TokenNameLESS:
						count++;
						break;
					case TerminalTokens.TokenNameGREATER:
						count--;
						if (count == 0) {
							end = this.positionScanner.currentPosition - 1;
							break loop;
						}
						break;
					case TerminalTokens.TokenNameLBRACE :
						break loop;
				}
			}
		} catch(InvalidInputException e) {
			// ignore
		}
		return end;
	}
	private int retrieveEndingPositionAfterOpeningParenthesis(int sourceStart, int sourceEnd, int numberOfParen) {
		if (this.referenceContext == null) return sourceEnd;
		CompilationResult compilationResult = this.referenceContext.compilationResult();
		if (compilationResult == null) return sourceEnd;
		ICompilationUnit compilationUnit = compilationResult.getCompilationUnit();
		if (compilationUnit == null) return sourceEnd;
		char[] contents = compilationUnit.getContents();
		if (contents.length == 0) return sourceEnd;
		if (this.positionScanner == null) {
			this.positionScanner = new Scanner(false, false, false, this.options.sourceLevel, this.options.complianceLevel, null, null, false);
		}
		this.positionScanner.setSource(contents);
		this.positionScanner.resetTo(sourceStart, sourceEnd);
		try {
			int token;
			int previousSourceEnd = sourceEnd;
			while ((token = this.positionScanner.getNextToken()) != TerminalTokens.TokenNameEOF) {
				switch(token) {
					case TerminalTokens.TokenNameRPAREN:
						return previousSourceEnd;
					default :
						previousSourceEnd = this.positionScanner.currentPosition - 1;
				}
			}
		} catch(InvalidInputException e) {
			// ignore
		}
		return sourceEnd;
	}
	private int retrieveStartingPositionAfterOpeningParenthesis(int sourceStart, int sourceEnd, int numberOfParen) {
		if (this.referenceContext == null) return sourceStart;
		CompilationResult compilationResult = this.referenceContext.compilationResult();
		if (compilationResult == null) return sourceStart;
		ICompilationUnit compilationUnit = compilationResult.getCompilationUnit();
		if (compilationUnit == null) return sourceStart;
		char[] contents = compilationUnit.getContents();
		if (contents.length == 0) return sourceStart;
		if (this.positionScanner == null) {
			this.positionScanner = new Scanner(false, false, false, this.options.sourceLevel, this.options.complianceLevel, null, null, false);
		}
		this.positionScanner.setSource(contents);
		this.positionScanner.resetTo(sourceStart, sourceEnd);
		int count = 0;
		try {
			int token;
			while ((token = this.positionScanner.getNextToken()) != TerminalTokens.TokenNameEOF) {
				switch(token) {
					case TerminalTokens.TokenNameLPAREN:
						count++;
						if (count == numberOfParen) {
							this.positionScanner.getNextToken();
							return this.positionScanner.startPosition;
						}
				}
			}
		} catch(InvalidInputException e) {
			// ignore
		}
		return sourceStart;
	}
	public void scannerError(Parser parser, String errorTokenName) {
		Scanner scanner = parser.scanner;
	
		int flag = IProblem.ParsingErrorNoSuggestion;
		int startPos = scanner.startPosition;
		int endPos = scanner.currentPosition - 1;
	
		//special treatment for recognized errors....
		if (errorTokenName.equals(Scanner.END_OF_SOURCE))
			flag = IProblem.EndOfSource;
		else if (errorTokenName.equals(Scanner.INVALID_HEXA))
			flag = IProblem.InvalidHexa;
		else if (errorTokenName.equals(Scanner.ILLEGAL_HEXA_LITERAL))
			flag = IProblem.IllegalHexaLiteral;
		else if (errorTokenName.equals(Scanner.INVALID_OCTAL))
			flag = IProblem.InvalidOctal;
		else if (errorTokenName.equals(Scanner.INVALID_CHARACTER_CONSTANT))
			flag = IProblem.InvalidCharacterConstant;
		else if (errorTokenName.equals(Scanner.INVALID_ESCAPE))
			flag = IProblem.InvalidEscape;
		else if (errorTokenName.equals(Scanner.INVALID_UNICODE_ESCAPE)){
			flag = IProblem.InvalidUnicodeEscape;
			// better locate the error message
			char[] source = scanner.source;
			int checkPos = scanner.currentPosition - 1;
			if (checkPos >= source.length) checkPos = source.length - 1;
			while (checkPos >= startPos){
				if (source[checkPos] == '\\') break;
				checkPos --;
			}
			startPos = checkPos;
		} else if (errorTokenName.equals(Scanner.INVALID_LOW_SURROGATE)) {
			flag = IProblem.InvalidLowSurrogate;
		} else if (errorTokenName.equals(Scanner.INVALID_HIGH_SURROGATE)) {
			flag = IProblem.InvalidHighSurrogate;
			// better locate the error message
			char[] source = scanner.source;
			int checkPos = scanner.startPosition + 1;
			while (checkPos <= endPos){
				if (source[checkPos] == '\\') break;
				checkPos ++;
			}
			endPos = checkPos - 1;
		} else if (errorTokenName.equals(Scanner.INVALID_FLOAT))
			flag = IProblem.InvalidFloat;
		else if (errorTokenName.equals(Scanner.UNTERMINATED_STRING))
			flag = IProblem.UnterminatedString;
		else if (errorTokenName.equals(Scanner.UNTERMINATED_COMMENT))
			flag = IProblem.UnterminatedComment;
		else if (errorTokenName.equals(Scanner.INVALID_CHAR_IN_STRING))
			flag = IProblem.UnterminatedString;
		else if (errorTokenName.equals(Scanner.INVALID_DIGIT))
			flag = IProblem.InvalidDigit;
		else if (errorTokenName.equals(Scanner.INVALID_BINARY))
			flag = IProblem.InvalidBinary;
		else if (errorTokenName.equals(Scanner.BINARY_LITERAL_NOT_BELOW_17))
			flag = IProblem.BinaryLiteralNotBelow17;
		else if (errorTokenName.equals(Scanner.INVALID_UNDERSCORE))
			flag = IProblem.IllegalUnderscorePosition;
		else if (errorTokenName.equals(Scanner.UNDERSCORES_IN_LITERALS_NOT_BELOW_17))
			flag = IProblem.UnderscoresInLiteralsNotBelow17;
	
		String[] arguments = flag == IProblem.ParsingErrorNoSuggestion
				? new String[] {errorTokenName}
				: NoArgument;
		this.handle(
			flag,
			arguments,
			arguments,
			// this is the current -invalid- token position
			startPos,
			endPos,
			parser.compilationUnit.compilationResult);
	}
	public void shouldImplementHashcode(SourceTypeBinding type) {	
		this.handle(
			IProblem.ShouldImplementHashcode,
			new String[] {new String(type.readableName())},
			new String[] {new String(type.shortReadableName())},
			type.sourceStart(),
			type.sourceEnd());
	}
	public void shouldReturn(TypeBinding returnType, ASTNode location) {
		int sourceStart = location.sourceStart;
		int sourceEnd = location.sourceEnd;
		if (location instanceof LambdaExpression) {
			LambdaExpression exp = (LambdaExpression) location;
			sourceStart = exp.sourceStart;
			sourceEnd = exp.diagnosticsSourceEnd();
		}
		this.handle(
			methodHasMissingSwitchDefault() ? IProblem.ShouldReturnValueHintMissingDefault : IProblem.ShouldReturnValue,
			new String[] { new String (returnType.readableName())},
			new String[] { new String (returnType.shortReadableName())},
			sourceStart,
			sourceEnd);
	}
	
	public void signalNoImplicitStringConversionForCharArrayExpression(Expression expression) {
		this.handle(
			IProblem.NoImplicitStringConversionForCharArrayExpression,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void staticAndInstanceConflict(MethodBinding currentMethod, MethodBinding inheritedMethod) {
		if (currentMethod.isStatic())
			this.handle(
				// This static method cannot hide the instance method from %1
				// 8.4.6.4 - If a class inherits more than one method with the same signature a static (non-abstract) method cannot hide an instance method.
				IProblem.CannotHideAnInstanceMethodWithAStaticMethod,
				new String[] {new String(inheritedMethod.declaringClass.readableName())},
				new String[] {new String(inheritedMethod.declaringClass.shortReadableName())},
				currentMethod.sourceStart(),
				currentMethod.sourceEnd());
		else
			this.handle(
				// This instance method cannot override the static method from %1
				// 8.4.6.4 - If a class inherits more than one method with the same signature an instance (non-abstract) method cannot override a static method.
				IProblem.CannotOverrideAStaticMethodWithAnInstanceMethod,
				new String[] {new String(inheritedMethod.declaringClass.readableName())},
				new String[] {new String(inheritedMethod.declaringClass.shortReadableName())},
				currentMethod.sourceStart(),
				currentMethod.sourceEnd());
	}
	public void staticFieldAccessToNonStaticVariable(ASTNode location, FieldBinding field) {
		String[] arguments = new String[] {new String(field.readableName())};
		this.handle(
			IProblem.NonStaticFieldFromStaticInvocation,
			arguments,
			arguments,
			nodeSourceStart(field,location),
			nodeSourceEnd(field, location));
	}
	public void staticInheritedMethodConflicts(SourceTypeBinding type, MethodBinding concreteMethod, MethodBinding[] abstractMethods) {
		this.handle(
			// The static method %1 conflicts with the abstract method in %2
			// 8.4.6.4 - If a class inherits more than one method with the same signature it is an error for one to be static (non-abstract) and the other abstract.
			IProblem.StaticInheritedMethodConflicts,
			new String[] {
				new String(concreteMethod.readableName()),
				new String(abstractMethods[0].declaringClass.readableName())},
			new String[] {
				new String(concreteMethod.readableName()),
				new String(abstractMethods[0].declaringClass.shortReadableName())},
			type.sourceStart(),
			type.sourceEnd());
	}
	public void staticMemberOfParameterizedType(ASTNode location, ReferenceBinding type, int index) {
		if (location == null) { // binary case
		    this.handle(
				IProblem.StaticMemberOfParameterizedType,
				new String[] {new String(type.readableName()), new String(type.enclosingType().readableName()), },
				new String[] {new String(type.shortReadableName()), new String(type.enclosingType().shortReadableName()), },
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
		    return;
		}
		/*if (location instanceof ArrayTypeReference) {
			ArrayTypeReference arrayTypeReference = (ArrayTypeReference) location;
			if (arrayTypeReference.token != null && arrayTypeReference.token.length == 0) return;
			end = arrayTypeReference.originalSourceEnd;
		}*/
	    this.handle(
			IProblem.StaticMemberOfParameterizedType,
			new String[] {new String(type.readableName()), new String(type.enclosingType().readableName()), },
			new String[] {new String(type.shortReadableName()), new String(type.enclosingType().shortReadableName()), },
			location.sourceStart,
			nodeSourceEnd(null, location, index));
	}
	public void stringConstantIsExceedingUtf8Limit(ASTNode location) {
		this.handle(
			IProblem.StringConstantIsExceedingUtf8Limit,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void superclassMustBeAClass(SourceTypeBinding type, TypeReference superclassRef, ReferenceBinding superType) {
		this.handle(
			IProblem.SuperclassMustBeAClass,
			new String[] {new String(superType.readableName()), new String(type.sourceName())},
			new String[] {new String(superType.shortReadableName()), new String(type.sourceName())},
			superclassRef.sourceStart,
			superclassRef.sourceEnd);
	}
	public void superfluousSemicolon(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.SuperfluousSemicolon,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void superinterfaceMustBeAnInterface(SourceTypeBinding type, TypeReference superInterfaceRef, ReferenceBinding superType) {
		this.handle(
			IProblem.SuperInterfaceMustBeAnInterface,
			new String[] {new String(superType.readableName()), new String(type.sourceName())},
			new String[] {new String(superType.shortReadableName()), new String(type.sourceName())},
			superInterfaceRef.sourceStart,
			superInterfaceRef.sourceEnd);
	}
	public void superinterfacesCollide(TypeBinding type, ASTNode decl, TypeBinding superType, TypeBinding inheritedSuperType) {
		this.handle(
			IProblem.SuperInterfacesCollide,
			new String[] {new String(superType.readableName()), new String(inheritedSuperType.readableName()), new String(type.sourceName())},
			new String[] {new String(superType.shortReadableName()), new String(inheritedSuperType.shortReadableName()), new String(type.sourceName())},
			decl.sourceStart,
			decl.sourceEnd);
	}
	public void superTypeCannotUseWildcard(SourceTypeBinding type, TypeReference superclass, TypeBinding superTypeBinding) {
		String name = new String(type.sourceName());
		String superTypeFullName = new String(superTypeBinding.readableName());
		String superTypeShortName = new String(superTypeBinding.shortReadableName());
		if (superTypeShortName.equals(name)) superTypeShortName = superTypeFullName;
		this.handle(
			IProblem.SuperTypeUsingWildcard,
			new String[] {superTypeFullName, name},
			new String[] {superTypeShortName, name},
			superclass.sourceStart,
			superclass.sourceEnd);
	}
	private void syntaxError(
		int id,
		int startPosition,
		int endPosition,
		int currentKind,
		char[] currentTokenSource,
		String errorTokenName,
		String expectedToken) {
	
		if (currentKind == TerminalTokens.TokenNameAT && expectedToken != null && expectedToken.equals("@")) { //$NON-NLS-1$
			// In the diagnose parser case, we don't have the wherewithal to discriminate when we should hand out @308 vs @. So we always answer @.
			// We should silently recover so swallow the message.
			return;
		}
		String eTokenName;
		if (isKeyword(currentKind) ||
			isLiteral(currentKind) ||
			isIdentifier(currentKind)) {
				eTokenName = new String(currentTokenSource);
		} else {
			eTokenName = errorTokenName;
		}
	
		String[] arguments;
		if(expectedToken != null) {
			expectedToken = replaceIfSynthetic(expectedToken);
			arguments = new String[] {eTokenName, expectedToken};
		} else {
			arguments = new String[] {eTokenName};
		}
		this.handle(
			id,
			arguments,
			arguments,
			startPosition,
			endPosition);
	}
	private String replaceIfSynthetic(String token) {
		/* Java 8 grammar changes use some synthetic tokens to make the grammar LALR(1). These tokens should not be exposed in messages
		   as it would make no sense to the programmer whatsoever. Replace such artificial tokens with some "suitable"  alternative. At
		   the moment, there are two synthetic tokens that need such massaging viz : "BeginLambda" and "BeginTypeArguments". There is a
		   third synthetic token "ElidedSemicolonAndRightBrace" that we don't expect to show up in messages since it is manufactured by
		   the parser automatically.
		*/
		if (token.equals("BeginTypeArguments")) //$NON-NLS-1$
			return "."; //$NON-NLS-1$
		if (token.equals("BeginLambda")) //$NON-NLS-1$
			return "("; //$NON-NLS-1$
		return token;
	}
	public void task(String tag, String message, String priority, int start, int end){
		this.handle(
			IProblem.Task,
			new String[] { tag, message, priority/*secret argument that is not surfaced in getMessage()*/},
			new String[] { tag, message, priority/*secret argument that is not surfaced in getMessage()*/},
			start,
			end);
	}
	
	public void tooManyDimensions(ASTNode expression) {
		this.handle(
			IProblem.TooManyArrayDimensions,
			NoArgument,
			NoArgument,
			expression.sourceStart,
			expression.sourceEnd);
	}
	
	public void tooManyFields(TypeDeclaration typeDeclaration) {
		this.handle(
			IProblem.TooManyFields,
			new String[]{ new String(typeDeclaration.binding.readableName())},
			new String[]{ new String(typeDeclaration.binding.shortReadableName())},
			ProblemSeverities.Abort | ProblemSeverities.Error | ProblemSeverities.Fatal,
			typeDeclaration.sourceStart,
			typeDeclaration.sourceEnd);
	}
	public void tooManyMethods(TypeDeclaration typeDeclaration) {
		this.handle(
			IProblem.TooManyMethods,
			new String[]{ new String(typeDeclaration.binding.readableName())},
			new String[]{ new String(typeDeclaration.binding.shortReadableName())},
			ProblemSeverities.Abort | ProblemSeverities.Error | ProblemSeverities.Fatal,
			typeDeclaration.sourceStart,
			typeDeclaration.sourceEnd);
	}
	public void tooManyParametersForSyntheticMethod(AbstractMethodDeclaration method) {
		MethodBinding binding = method.binding;
		String selector = null;
		if (binding.isConstructor()) {
			selector = new String(binding.declaringClass.sourceName());
		} else {
			selector = new String(method.selector);
		}
		this.handle(
			IProblem.TooManyParametersForSyntheticMethod,
			new String[] {selector, typesAsString(binding, false), new String(binding.declaringClass.readableName()), },
			new String[] {selector, typesAsString(binding, true), new String(binding.declaringClass.shortReadableName()),},
			ProblemSeverities.AbortMethod | ProblemSeverities.Error | ProblemSeverities.Fatal,
			method.sourceStart,
			method.sourceEnd);
	}
	public void typeCastError(CastExpression expression, TypeBinding leftType, TypeBinding rightType) {
		String leftName = new String(leftType.readableName());
		String rightName = new String(rightType.readableName());
		String leftShortName = new String(leftType.shortReadableName());
		String rightShortName = new String(rightType.shortReadableName());
		if (leftShortName.equals(rightShortName)){
			leftShortName = leftName;
			rightShortName = rightName;
		}
		this.handle(
			IProblem.IllegalCast,
			new String[] { rightName, leftName },
			new String[] { rightShortName, leftShortName },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void typeCollidesWithEnclosingType(TypeDeclaration typeDecl) {
		String[] arguments = new String[] {new String(typeDecl.name)};
		this.handle(
			IProblem.HidingEnclosingType,
			arguments,
			arguments,
			typeDecl.sourceStart,
			typeDecl.sourceEnd);
	}
	public void typeCollidesWithPackage(CompilationUnitDeclaration compUnitDecl, TypeDeclaration typeDecl) {
		this.referenceContext = typeDecl; // report the problem against the type not the entire compilation unit
		String[] arguments = new String[] {new String(compUnitDecl.getFileName()), new String(typeDecl.name)};
		this.handle(
			IProblem.TypeCollidesWithPackage,
			arguments,
			arguments,
			typeDecl.sourceStart,
			typeDecl.sourceEnd,
			compUnitDecl.compilationResult);
	}
	public void typeHiding(TypeDeclaration typeDecl, TypeBinding hiddenType) {
		int severity = computeSeverity(IProblem.TypeHidingType);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.TypeHidingType,
			new String[] { new String(typeDecl.name) , new String(hiddenType.shortReadableName()) },
			new String[] { new String(typeDecl.name) , new String(hiddenType.readableName()) },
			severity,
			typeDecl.sourceStart,
			typeDecl.sourceEnd);
	}
	public void typeHiding(TypeDeclaration typeDecl, TypeVariableBinding hiddenTypeParameter) {
		int severity = computeSeverity(IProblem.TypeHidingTypeParameterFromType);
		if (severity == ProblemSeverities.Ignore) return;
		if (hiddenTypeParameter.declaringElement instanceof TypeBinding) {
			TypeBinding declaringType = (TypeBinding) hiddenTypeParameter.declaringElement;
			this.handle(
				IProblem.TypeHidingTypeParameterFromType,
				new String[] { new String(typeDecl.name) , new String(hiddenTypeParameter.readableName()), new String(declaringType.readableName())  },
				new String[] { new String(typeDecl.name) , new String(hiddenTypeParameter.shortReadableName()), new String(declaringType.shortReadableName()) },
				severity,
				typeDecl.sourceStart,
				typeDecl.sourceEnd);
		} else {
			// type parameter of generic method
			MethodBinding declaringMethod = (MethodBinding) hiddenTypeParameter.declaringElement;
			this.handle(
					IProblem.TypeHidingTypeParameterFromMethod,
					new String[] {
							new String(typeDecl.name),
							new String(hiddenTypeParameter.readableName()),
							new String(declaringMethod.selector),
							typesAsString(declaringMethod, false),
							new String(declaringMethod.declaringClass.readableName()),
					},
					new String[] {
							new String(typeDecl.name),
							new String(hiddenTypeParameter.shortReadableName()),
							new String(declaringMethod.selector),
							typesAsString(declaringMethod, true),
							new String(declaringMethod.declaringClass.shortReadableName()),
					},
					severity,
					typeDecl.sourceStart,
					typeDecl.sourceEnd);
		}
	}
	public void typeHiding(TypeParameter typeParam, Binding hidden) {
		int severity = computeSeverity(IProblem.TypeParameterHidingType);
		if (severity == ProblemSeverities.Ignore) return;
		TypeBinding hiddenType = (TypeBinding) hidden;
		this.handle(
			IProblem.TypeParameterHidingType,
			new String[] { new String(typeParam.name) , new String(hiddenType.readableName())  },
			new String[] { new String(typeParam.name) , new String(hiddenType.shortReadableName()) },
			severity,
			typeParam.sourceStart,
			typeParam.sourceEnd);
	}
	public void typeMismatchError(TypeBinding actualType, TypeBinding expectedType, ASTNode location, ASTNode expectingLocation) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) { // don't expose type variable names, complain on erased types
			if (actualType instanceof TypeVariableBinding)
				actualType = actualType.erasure();
			if (expectedType instanceof TypeVariableBinding)
				expectedType = expectedType.erasure();
		}
		if (actualType != null && (actualType.tagBits & TagBits.HasMissingType) != 0) { // improve secondary error
			if (location instanceof Annotation) {
				// https://bugs.eclipse.org/bugs/show_bug.cgi?id=376977
				return; // Already reported, don't report a secondary error
			}
			this.handle(
					IProblem.UndefinedType,
					new String[] {new String(actualType.leafComponentType().readableName())},
					new String[] {new String(actualType.leafComponentType().shortReadableName())},
					location.sourceStart,
					location.sourceEnd);
				return;
		}
		if (expectingLocation != null && (expectedType.tagBits & TagBits.HasMissingType) != 0) { // improve secondary error
			this.handle(
					IProblem.UndefinedType,
					new String[] {new String(expectedType.leafComponentType().readableName())},
					new String[] {new String(expectedType.leafComponentType().shortReadableName())},
					expectingLocation.sourceStart,
					expectingLocation.sourceEnd);
				return;
		}
	
		char[] actualShortReadableName = actualType.shortReadableName();
		char[] expectedShortReadableName = expectedType.shortReadableName();
		char[] actualReadableName = actualType.readableName();
		char[] expectedReadableName = expectedType.readableName();
		if (CharOperation.equals(actualShortReadableName, expectedShortReadableName)) {
			if (CharOperation.equals(actualReadableName, expectedReadableName)) {
				// if full type names are equal, assume the incompatibility is due to mismatching null annotations:
				actualReadableName = actualType.nullAnnotatedReadableName(this.options, false);
				expectedReadableName = expectedType.nullAnnotatedReadableName(this.options, false);
				actualShortReadableName = actualType.nullAnnotatedReadableName(this.options, true);
				expectedShortReadableName = expectedType.nullAnnotatedReadableName(this.options, true);
			} else {
				actualShortReadableName = actualReadableName;
				expectedShortReadableName = expectedReadableName;
			}
		}
		this.handle(
			expectingLocation instanceof ReturnStatement ? IProblem.ReturnTypeMismatch : IProblem.TypeMismatch,
			new String[] {new String(actualReadableName), new String(expectedReadableName)},
			new String[] {new String(actualShortReadableName), new String(expectedShortReadableName)},
			location.sourceStart,
			location.sourceEnd);
	}
	public void typeMismatchError(TypeBinding typeArgument, TypeVariableBinding typeParameter, ReferenceBinding genericType, ASTNode location) {
		if (location == null) { // binary case
			this.handle(
				IProblem.TypeArgumentMismatch,
				new String[] { new String(typeArgument.readableName()), new String(genericType.readableName()), new String(typeParameter.sourceName()), parameterBoundAsString(typeParameter, false) },
				new String[] { new String(typeArgument.shortReadableName()), new String(genericType.shortReadableName()), new String(typeParameter.sourceName()), parameterBoundAsString(typeParameter, true) },
				ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
				0,
				0);
	        return;
	    }
		this.handle(
			IProblem.TypeArgumentMismatch,
			new String[] { new String(typeArgument.readableName()), new String(genericType.readableName()), new String(typeParameter.sourceName()), parameterBoundAsString(typeParameter, false) },
			new String[] { new String(typeArgument.shortReadableName()), new String(genericType.shortReadableName()), new String(typeParameter.sourceName()), parameterBoundAsString(typeParameter, true) },
			location.sourceStart,
			location.sourceEnd);
	}
	private String typesAsString(MethodBinding methodBinding, boolean makeShort) {
		return typesAsString(methodBinding, methodBinding.parameters, makeShort);
	}
	private String typesAsString(MethodBinding methodBinding, TypeBinding[] parameters, boolean makeShort) {
		return typesAsString(methodBinding, parameters, makeShort, false);
	}
	private String typesAsString(MethodBinding methodBinding, boolean makeShort, boolean showNullAnnotations) {
		return typesAsString(methodBinding, methodBinding.parameters, makeShort, showNullAnnotations);
	}
	private String typesAsString(MethodBinding methodBinding, TypeBinding[] parameters, boolean makeShort, boolean showNullAnnotations) {
		if (methodBinding.isPolymorphic()) {
			// get the original polymorphicMethod method
			TypeBinding[] types = methodBinding.original().parameters;
			StringBuffer buffer = new StringBuffer(10);
			for (int i = 0, length = types.length; i < length; i++) {
				if (i != 0) {
					buffer.append(", "); //$NON-NLS-1$
				}
				TypeBinding type = types[i];
				boolean isVarargType = i == length-1;
				if (isVarargType) {
					type = ((ArrayBinding)type).elementsType();
				}
				if (showNullAnnotations)
					buffer.append(new String(type.nullAnnotatedReadableName(this.options, makeShort)));
				else
					buffer.append(new String(makeShort ? type.shortReadableName() : type.readableName()));
				if (isVarargType) {
					buffer.append("..."); //$NON-NLS-1$
				}
			}
			return buffer.toString();
		}
		StringBuffer buffer = new StringBuffer(10);
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
			}
			TypeBinding type = parameters[i];
			boolean isVarargType = methodBinding.isVarargs() && i == length-1;
			if (isVarargType) {
				type = ((ArrayBinding)type).elementsType();
			}
			if (showNullAnnotations)
				buffer.append(new String(type.nullAnnotatedReadableName(this.options, makeShort)));
			else
				buffer.append(new String(makeShort ? type.shortReadableName() : type.readableName()));
			if (isVarargType) {
				buffer.append("..."); //$NON-NLS-1$
			}
		}
		return buffer.toString();
	}
	private String typesAsString(TypeBinding[] types, boolean makeShort) {
		return typesAsString(types, makeShort, false);
	}
	private String typesAsString(TypeBinding[] types, boolean makeShort, boolean showNullAnnotations) {
		StringBuffer buffer = new StringBuffer(10);
		for (int i = 0, length = types.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
			}
			TypeBinding type = types[i];
			if (showNullAnnotations)
				buffer.append(new String(type.nullAnnotatedReadableName(this.options, makeShort)));
			else
				buffer.append(new String(makeShort ? type.shortReadableName() : type.readableName()));
		}
		return buffer.toString();
	}
	
	public void undefinedAnnotationValue(TypeBinding annotationType, MemberValuePair memberValuePair) {
		if (isRecoveredName(memberValuePair.name)) return;
		String name = 	new String(memberValuePair.name);
		this.handle(
			IProblem.UndefinedAnnotationMember,
			new String[] { name, new String(annotationType.readableName())},
			new String[] {	name, new String(annotationType.shortReadableName())},
			memberValuePair.sourceStart,
			memberValuePair.sourceEnd);
	}
	public void undefinedLabel(BranchStatement statement) {
		if (isRecoveredName(statement.label)) return;
		String[] arguments = new String[] {new String(statement.label)};
		this.handle(
			IProblem.UndefinedLabel,
			arguments,
			arguments,
			statement.sourceStart,
			statement.sourceEnd);
	}
	// can only occur inside binaries
	public void undefinedTypeVariableSignature(char[] variableName, ReferenceBinding binaryType) {
		this.handle(
			IProblem.UndefinedTypeVariable,
			new String[] {new String(variableName), new String(binaryType.readableName()) },
			new String[] {new String(variableName), new String(binaryType.shortReadableName())},
			ProblemSeverities.AbortCompilation | ProblemSeverities.Error | ProblemSeverities.Fatal,
			0,
			0);
	}
	public void undocumentedEmptyBlock(int blockStart, int blockEnd) {
		this.handle(
			IProblem.UndocumentedEmptyBlock,
			NoArgument,
			NoArgument,
			blockStart,
			blockEnd);
	}
	public void unexpectedStaticModifierForField(SourceTypeBinding type, FieldDeclaration fieldDecl) {
		String[] arguments = new String[] {new String(fieldDecl.name)};
		this.handle(
			IProblem.UnexpectedStaticModifierForField,
			arguments,
			arguments,
			fieldDecl.sourceStart,
			fieldDecl.sourceEnd);
	}
	public void unexpectedStaticModifierForMethod(ReferenceBinding type, AbstractMethodDeclaration methodDecl) {
		String[] arguments = new String[] {new String(type.sourceName()), new String(methodDecl.selector)};
		this.handle(
			IProblem.UnexpectedStaticModifierForMethod,
			arguments,
			arguments,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	public void unhandledException(TypeBinding exceptionType, ASTNode location) {
	
		boolean insideDefaultConstructor =
			(this.referenceContext instanceof ConstructorDeclaration)
				&& ((ConstructorDeclaration)this.referenceContext).isDefaultConstructor();
		boolean insideImplicitConstructorCall =
			(location instanceof ExplicitConstructorCall)
				&& (((ExplicitConstructorCall) location).accessMode == ExplicitConstructorCall.ImplicitSuper);
	
		int sourceEnd = location.sourceEnd;
		if (location instanceof LocalDeclaration) {
			sourceEnd = ((LocalDeclaration) location).declarationEnd;
		}
		this.handle(
			insideDefaultConstructor
				? IProblem.UnhandledExceptionInDefaultConstructor
				: (insideImplicitConstructorCall
						? IProblem.UndefinedConstructorInImplicitConstructorCall
						: IProblem.UnhandledException),
			new String[] {new String(exceptionType.readableName())},
			new String[] {new String(exceptionType.shortReadableName())},
			location.sourceStart,
			sourceEnd);
	}
	public void unhandledExceptionFromAutoClose (TypeBinding exceptionType, ASTNode location) {
		LocalVariableBinding localBinding = ((LocalDeclaration)location).binding;
		if (localBinding != null) {
			this.handle(
				IProblem.UnhandledExceptionOnAutoClose,
				new String[] {
						new String(exceptionType.readableName()),
						new String(localBinding.readableName())},
				new String[] {
						new String(exceptionType.shortReadableName()),
						new String(localBinding.shortReadableName())},
				location.sourceStart,
				location.sourceEnd);
		}
	}
	public void unhandledWarningToken(Expression token) {
		String[] arguments = new String[] { token.constant.stringValue() };
		this.handle(
			IProblem.UnhandledWarningToken,
			arguments,
			arguments,
			token.sourceStart,
			token.sourceEnd);
	}
	public void uninitializedBlankFinalField(FieldBinding field, ASTNode location) {
		String[] arguments = new String[] {new String(field.readableName())};
		this.handle(
			methodHasMissingSwitchDefault() ? IProblem.UninitializedBlankFinalFieldHintMissingDefault : IProblem.UninitializedBlankFinalField,
			arguments,
			arguments,
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	public void uninitializedNonNullField(FieldBinding field, ASTNode location) {
		char[][] nonNullAnnotationName = this.options.nonNullAnnotationName;
		String[] arguments = new String[] {
				new String(nonNullAnnotationName[nonNullAnnotationName.length-1]),
				new String(field.readableName())
		};
		this.handle(
			methodHasMissingSwitchDefault() ? IProblem.UninitializedNonNullFieldHintMissingDefault : IProblem.UninitializedNonNullField,
			arguments,
			arguments,
			nodeSourceStart(field, location),
			nodeSourceEnd(field, location));
	}
	public void uninitializedLocalVariable(LocalVariableBinding binding, ASTNode location) {
		binding.tagBits |= TagBits.NotInitialized;
		String[] arguments = new String[] {new String(binding.readableName())};
		this.handle(
			methodHasMissingSwitchDefault() ? IProblem.UninitializedLocalVariableHintMissingDefault : IProblem.UninitializedLocalVariable,
			arguments,
			arguments,
			nodeSourceStart(binding, location),
			nodeSourceEnd(binding, location));
	}
	private boolean methodHasMissingSwitchDefault() {
		MethodScope methodScope = null;
		if (this.referenceContext instanceof Block) {
			methodScope = ((Block)this.referenceContext).scope.methodScope();
		} else if (this.referenceContext instanceof AbstractMethodDeclaration) {
			methodScope = ((AbstractMethodDeclaration)this.referenceContext).scope;
		}
		return methodScope != null && methodScope.hasMissingSwitchDefault;	
	}
	public void unmatchedBracket(int position, ReferenceContext context, CompilationResult compilationResult) {
		this.handle(
			IProblem.UnmatchedBracket,
			NoArgument,
			NoArgument,
			position,
			position,
			context,
			compilationResult);
	}
	public void unnecessaryCast(CastExpression castExpression) {
		if (castExpression.expression instanceof FunctionalExpression)
			return;
		int severity = computeSeverity(IProblem.UnnecessaryCast);
		if (severity == ProblemSeverities.Ignore) return;
		TypeBinding castedExpressionType = castExpression.expression.resolvedType;
		this.handle(
			IProblem.UnnecessaryCast,
			new String[]{ new String(castedExpressionType.readableName()), new String(castExpression.type.resolvedType.readableName())},
			new String[]{ new String(castedExpressionType.shortReadableName()), new String(castExpression.type.resolvedType.shortReadableName())},
			severity,
			castExpression.sourceStart,
			castExpression.sourceEnd);
	}
	public void unnecessaryElse(ASTNode location) {
		this.handle(
			IProblem.UnnecessaryElse,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void unnecessaryEnclosingInstanceSpecification(Expression expression, ReferenceBinding targetType) {
		this.handle(
			IProblem.IllegalEnclosingInstanceSpecification,
			new String[]{ new String(targetType.readableName())},
			new String[]{ new String(targetType.shortReadableName())},
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void unnecessaryInstanceof(InstanceOfExpression instanceofExpression, TypeBinding checkType) {
		int severity = computeSeverity(IProblem.UnnecessaryInstanceof);
		if (severity == ProblemSeverities.Ignore) return;
		TypeBinding expressionType = instanceofExpression.expression.resolvedType;
		this.handle(
			IProblem.UnnecessaryInstanceof,
			new String[]{ new String(expressionType.readableName()), new String(checkType.readableName())},
			new String[]{ new String(expressionType.shortReadableName()), new String(checkType.shortReadableName())},
			severity,
			instanceofExpression.sourceStart,
			instanceofExpression.sourceEnd);
	}
	public void unnecessaryNLSTags(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.UnnecessaryNLSTag,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void unnecessaryTypeArgumentsForMethodInvocation(MethodBinding method, TypeBinding[] genericTypeArguments, TypeReference[] typeArguments) {
		String methodName = method.isConstructor()
			? new String(method.declaringClass.shortReadableName())
			: new String(method.selector);
		this.handle(
				method.isConstructor()
					? IProblem.UnusedTypeArgumentsForConstructorInvocation
					: IProblem.UnusedTypeArgumentsForMethodInvocation,
			new String[] {
					methodName,
			        typesAsString(method, false),
			        new String(method.declaringClass.readableName()),
			        typesAsString(genericTypeArguments, false) },
			new String[] {
					methodName,
			        typesAsString(method, true),
			        new String(method.declaringClass.shortReadableName()),
			        typesAsString(genericTypeArguments, true) },
			typeArguments[0].sourceStart,
			typeArguments[typeArguments.length-1].sourceEnd);
	}
	public void unqualifiedFieldAccess(NameReference reference, FieldBinding field) {
		int sourceStart = reference.sourceStart;
		int sourceEnd = reference.sourceEnd;
		if (reference instanceof SingleNameReference) {
			int numberOfParens = (reference.bits & ASTNode.ParenthesizedMASK) >> ASTNode.ParenthesizedSHIFT;
			if (numberOfParens != 0) {
				sourceStart = retrieveStartingPositionAfterOpeningParenthesis(sourceStart, sourceEnd, numberOfParens);
				sourceEnd = retrieveEndingPositionAfterOpeningParenthesis(sourceStart, sourceEnd, numberOfParens);
			} else {
				sourceStart = nodeSourceStart(field, reference);
				sourceEnd = nodeSourceEnd(field, reference);
			}
		} else {
			sourceStart = nodeSourceStart(field, reference);
			sourceEnd = nodeSourceEnd(field, reference);
		}
		this.handle(
			IProblem.UnqualifiedFieldAccess,
			new String[] {new String(field.declaringClass.readableName()), new String(field.name)},
			new String[] {new String(field.declaringClass.shortReadableName()), new String(field.name)},
			sourceStart,
			sourceEnd);
	}
	public void unreachableCatchBlock(ReferenceBinding exceptionType, ASTNode location) {
		this.handle(
			IProblem.UnreachableCatch,
			new String[] {
				new String(exceptionType.readableName()),
			 },
			new String[] {
				new String(exceptionType.shortReadableName()),
			 },
			location.sourceStart,
			location.sourceEnd);
	}
	public void unreachableCode(Statement statement) {
		int sourceStart = statement.sourceStart;
		int sourceEnd = statement.sourceEnd;
		if (statement instanceof LocalDeclaration) {
			LocalDeclaration declaration = (LocalDeclaration) statement;
			sourceStart = declaration.declarationSourceStart;
			sourceEnd = declaration.declarationSourceEnd;
		} else if (statement instanceof Expression) {
			int statemendEnd = ((Expression) statement).statementEnd;
			if (statemendEnd != -1) sourceEnd = statemendEnd;
		}
		this.handle(
			IProblem.CodeCannotBeReached,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void unresolvableReference(NameReference nameRef, Binding binding) {
	/* also need to check that the searchedType is the receiver type
		if (binding instanceof ProblemBinding) {
			ProblemBinding problem = (ProblemBinding) binding;
			if (problem.searchType != null && problem.searchType.isHierarchyInconsistent())
				severity = SecondaryError;
		}
	*/
		String[] arguments = new String[] {new String(binding.readableName())};
		int end = nameRef.sourceEnd;
		int sourceStart = nameRef.sourceStart;
		if (nameRef instanceof QualifiedNameReference) {
			QualifiedNameReference ref = (QualifiedNameReference) nameRef;
			if (isRecoveredName(ref.tokens)) return;
			if (ref.indexOfFirstFieldBinding >= 1)
				end = (int) ref.sourcePositions[ref.indexOfFirstFieldBinding - 1];
		} else {
			SingleNameReference ref = (SingleNameReference) nameRef;
			if (isRecoveredName(ref.token)) return;
			int numberOfParens = (ref.bits & ASTNode.ParenthesizedMASK) >> ASTNode.ParenthesizedSHIFT;
			if (numberOfParens != 0) {
				sourceStart = retrieveStartingPositionAfterOpeningParenthesis(sourceStart, end, numberOfParens);
				end = retrieveEndingPositionAfterOpeningParenthesis(sourceStart, end, numberOfParens);
			}
		}
		int problemId = (nameRef.bits & Binding.VARIABLE) != 0 && (nameRef.bits & Binding.TYPE) == 0
			? IProblem.UnresolvedVariable
			: IProblem.UndefinedName;
		this.handle(
			problemId,
			arguments,
			arguments,
			sourceStart,
			end);
	}
	public void unsafeCast(CastExpression castExpression, Scope scope) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		int severity = computeSeverity(IProblem.UnsafeGenericCast);
		if (severity == ProblemSeverities.Ignore) return;
		TypeBinding castedExpressionType = castExpression.expression.resolvedType;
		TypeBinding castExpressionResolvedType = castExpression.resolvedType;
		this.handle(
			IProblem.UnsafeGenericCast,
			new String[]{
				new String(castedExpressionType.readableName()),
				new String(castExpressionResolvedType.readableName())
			},
			new String[]{
				new String(castedExpressionType.shortReadableName()),
				new String(castExpressionResolvedType.shortReadableName())
			},
			severity,
			castExpression.sourceStart,
			castExpression.sourceEnd);
	}
	public void unsafeNullnessCast(CastExpression castExpression, Scope scope) {
		TypeBinding castedExpressionType = castExpression.expression.resolvedType;
		TypeBinding castExpressionResolvedType = castExpression.resolvedType;
		this.handle(
			IProblem.UnsafeNullnessCast,
			new String[]{
				new String(castedExpressionType.nullAnnotatedReadableName(this.options, false)),
				new String(castExpressionResolvedType.nullAnnotatedReadableName(this.options, false))
			},
			new String[]{
				new String(castedExpressionType.nullAnnotatedReadableName(this.options, true)),
				new String(castExpressionResolvedType.nullAnnotatedReadableName(this.options, true))
			},
			castExpression.sourceStart,
			castExpression.sourceEnd);
	}
	public void unsafeGenericArrayForVarargs(TypeBinding leafComponentType, ASTNode location) {
		int severity = computeSeverity(IProblem.UnsafeGenericArrayForVarargs);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.UnsafeGenericArrayForVarargs,
			new String[]{ new String(leafComponentType.readableName())},
			new String[]{ new String(leafComponentType.shortReadableName())},
			severity,
			location.sourceStart,
			location.sourceEnd);
	}
	public void unsafeRawFieldAssignment(FieldBinding field, TypeBinding expressionType, ASTNode location) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		int severity = computeSeverity(IProblem.UnsafeRawFieldAssignment);
		if (severity == ProblemSeverities.Ignore) return;
		this.handle(
			IProblem.UnsafeRawFieldAssignment,
			new String[] {
			        new String(expressionType.readableName()), new String(field.name), new String(field.declaringClass.readableName()), new String(field.declaringClass.erasure().readableName()) },
			new String[] {
			        new String(expressionType.shortReadableName()), new String(field.name), new String(field.declaringClass.shortReadableName()), new String(field.declaringClass.erasure().shortReadableName()) },
			severity,
			nodeSourceStart(field,location),
			nodeSourceEnd(field, location));
	}
	public void unsafeRawGenericMethodInvocation(ASTNode location, MethodBinding rawMethod, TypeBinding[] argumentTypes) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		boolean isConstructor = rawMethod.isConstructor();
		int severity = computeSeverity(isConstructor ? IProblem.UnsafeRawGenericConstructorInvocation : IProblem.UnsafeRawGenericMethodInvocation);
		if (severity == ProblemSeverities.Ignore) return;
	    if (isConstructor) {
			this.handle(
				IProblem.UnsafeRawGenericConstructorInvocation, // The generic constructor {0}({1}) of type {2} is applied to non-parameterized type arguments ({3})
				new String[] {
					new String(rawMethod.declaringClass.sourceName()),
					typesAsString(rawMethod.original(), false),
					new String(rawMethod.declaringClass.readableName()),
					typesAsString(argumentTypes, false),
				 },
				new String[] {
					new String(rawMethod.declaringClass.sourceName()),
					typesAsString(rawMethod.original(), true),
					new String(rawMethod.declaringClass.shortReadableName()),
					typesAsString(argumentTypes, true),
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
	    } else {
			this.handle(
				IProblem.UnsafeRawGenericMethodInvocation,
				new String[] {
					new String(rawMethod.selector),
					typesAsString(rawMethod.original(), false),
					new String(rawMethod.declaringClass.readableName()),
					typesAsString(argumentTypes, false),
				 },
				new String[] {
					new String(rawMethod.selector),
					typesAsString(rawMethod.original(), true),
					new String(rawMethod.declaringClass.shortReadableName()),
					typesAsString(argumentTypes, true),
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
	    }
	}
	public void unsafeRawInvocation(ASTNode location, MethodBinding rawMethod) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		boolean isConstructor = rawMethod.isConstructor();
		int severity = computeSeverity(isConstructor ? IProblem.UnsafeRawConstructorInvocation : IProblem.UnsafeRawMethodInvocation);
		if (severity == ProblemSeverities.Ignore) return;
	    if (isConstructor) {
			this.handle(
				IProblem.UnsafeRawConstructorInvocation,
				new String[] {
					new String(rawMethod.declaringClass.readableName()),
					typesAsString(rawMethod.original(), rawMethod.parameters, false),
					new String(rawMethod.declaringClass.erasure().readableName()),
				 },
				new String[] {
					new String(rawMethod.declaringClass.shortReadableName()),
					typesAsString(rawMethod.original(), rawMethod.parameters, true),
					new String(rawMethod.declaringClass.erasure().shortReadableName()),
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
	    } else {
			this.handle(
				IProblem.UnsafeRawMethodInvocation,
				new String[] {
					new String(rawMethod.selector),
					typesAsString(rawMethod.original(), rawMethod.parameters, false),
					new String(rawMethod.declaringClass.readableName()),
					new String(rawMethod.declaringClass.erasure().readableName()),
				 },
				new String[] {
					new String(rawMethod.selector),
					typesAsString(rawMethod.original(), rawMethod.parameters, true),
					new String(rawMethod.declaringClass.shortReadableName()),
					new String(rawMethod.declaringClass.erasure().shortReadableName()),
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
	    }
	}
	public void unsafeReturnTypeOverride(MethodBinding currentMethod, MethodBinding inheritedMethod, SourceTypeBinding type) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) {
			return;
		}
		int severity = computeSeverity(IProblem.UnsafeReturnTypeOverride);
		if (severity == ProblemSeverities.Ignore) return;
		int start = type.sourceStart();
		int end = type.sourceEnd();
		if (TypeBinding.equalsEquals(currentMethod.declaringClass, type)) {
			ASTNode location = ((MethodDeclaration) currentMethod.sourceMethod()).returnType;
			start = location.sourceStart();
			end = location.sourceEnd();
		}
		this.handle(
				IProblem.UnsafeReturnTypeOverride,
				new String[] {
					new String(currentMethod.returnType.readableName()),
					new String(currentMethod.selector),
					typesAsString(currentMethod.original(), false),
					new String(currentMethod.declaringClass.readableName()),
					new String(inheritedMethod.returnType.readableName()),
					new String(inheritedMethod.declaringClass.readableName()),
					//new String(inheritedMethod.returnType.erasure().readableName()),
				 },
				new String[] {
					new String(currentMethod.returnType.shortReadableName()),
					new String(currentMethod.selector),
					typesAsString(currentMethod.original(), true),
					new String(currentMethod.declaringClass.shortReadableName()),
					new String(inheritedMethod.returnType.shortReadableName()),
					new String(inheritedMethod.declaringClass.shortReadableName()),
					//new String(inheritedMethod.returnType.erasure().shortReadableName()),
				 },
				severity,
				start,
				end);
	}
	public void unsafeTypeConversion(Expression expression, TypeBinding expressionType, TypeBinding expectedType) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		int severity = computeSeverity(IProblem.UnsafeTypeConversion);
		if (severity == ProblemSeverities.Ignore) return;
		if (!this.options.reportUnavoidableGenericTypeProblems && expression.forcedToBeRaw(this.referenceContext)) {
			return;
		}
		this.handle(
			IProblem.UnsafeTypeConversion,
			new String[] { new String(expressionType.readableName()), new String(expectedType.readableName()), new String(expectedType.erasure().readableName()) },
			new String[] { new String(expressionType.shortReadableName()), new String(expectedType.shortReadableName()), new String(expectedType.erasure().shortReadableName()) },
			severity,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void unsafeElementTypeConversion(Expression expression, TypeBinding expressionType, TypeBinding expectedType) {
		if (this.options.sourceLevel < ClassFileConstants.JDK1_5) return; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305259
		int severity = computeSeverity(IProblem.UnsafeElementTypeConversion);
		if (severity == ProblemSeverities.Ignore) return;
		if (!this.options.reportUnavoidableGenericTypeProblems && expression.forcedToBeRaw(this.referenceContext)) {
			return;
		}
		this.handle(
			IProblem.UnsafeElementTypeConversion,
			new String[] { new String(expressionType.readableName()), new String(expectedType.readableName()), new String(expectedType.erasure().readableName()) },
			new String[] { new String(expressionType.shortReadableName()), new String(expectedType.shortReadableName()), new String(expectedType.erasure().shortReadableName()) },
			severity,
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void unusedArgument(LocalDeclaration localDecl) {
		int severity = computeSeverity(IProblem.ArgumentIsNeverUsed);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(localDecl.name)};
		this.handle(
			IProblem.ArgumentIsNeverUsed,
			arguments,
			arguments,
			severity,
			localDecl.sourceStart,
			localDecl.sourceEnd);
	}
	public void unusedDeclaredThrownException(ReferenceBinding exceptionType, AbstractMethodDeclaration method, ASTNode location) {
		boolean isConstructor = method.isConstructor();
		int severity = computeSeverity(isConstructor ? IProblem.UnusedConstructorDeclaredThrownException : IProblem.UnusedMethodDeclaredThrownException);
		if (severity == ProblemSeverities.Ignore) return;
		if (isConstructor) {
			this.handle(
				IProblem.UnusedConstructorDeclaredThrownException,
				new String[] {
					new String(method.binding.declaringClass.readableName()),
					typesAsString(method.binding, false),
					new String(exceptionType.readableName()),
				 },
				new String[] {
					new String(method.binding.declaringClass.shortReadableName()),
					typesAsString(method.binding, true),
					new String(exceptionType.shortReadableName()),
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
		} else {
			this.handle(
				IProblem.UnusedMethodDeclaredThrownException,
				new String[] {
					new String(method.binding.declaringClass.readableName()),
					new String(method.selector),
					typesAsString(method.binding, false),
					new String(exceptionType.readableName()),
				 },
				new String[] {
					new String(method.binding.declaringClass.shortReadableName()),
					new String(method.selector),
					typesAsString(method.binding, true),
					new String(exceptionType.shortReadableName()),
				 },
				severity,
				location.sourceStart,
				location.sourceEnd);
		}
	}
	public void unusedImport(ImportReference importRef) {
		int severity = computeSeverity(IProblem.UnusedImport);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] { CharOperation.toString(importRef.tokens) };
		this.handle(
			IProblem.UnusedImport,
			arguments,
			arguments,
			severity,
			importRef.sourceStart,
			importRef.sourceEnd);
	}
	public void unusedLabel(LabeledStatement statement) {
		int severity = computeSeverity(IProblem.UnusedLabel);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(statement.label)};
		this.handle(
			IProblem.UnusedLabel,
			arguments,
			arguments,
			severity,
			statement.sourceStart,
			statement.labelEnd);
	}
	public void unusedLocalVariable(LocalDeclaration localDecl) {
		int severity = computeSeverity(IProblem.LocalVariableIsNeverUsed);
		if (severity == ProblemSeverities.Ignore) return;
		String[] arguments = new String[] {new String(localDecl.name)};
		this.handle(
			IProblem.LocalVariableIsNeverUsed,
			arguments,
			arguments,
			severity,
			localDecl.sourceStart,
			localDecl.sourceEnd);
	}
	public void unusedObjectAllocation(AllocationExpression allocationExpression) {
		this.handle(
			IProblem.UnusedObjectAllocation, 
			NoArgument, 
			NoArgument, 
			allocationExpression.sourceStart, 
			allocationExpression.sourceEnd);
	}
	public void unusedPrivateConstructor(ConstructorDeclaration constructorDecl) {
	
		int severity = computeSeverity(IProblem.UnusedPrivateConstructor);
		if (severity == ProblemSeverities.Ignore) return;
		
		if (excludeDueToAnnotation(constructorDecl.annotations, IProblem.UnusedPrivateConstructor)) return;
		
		MethodBinding constructor = constructorDecl.binding;
		this.handle(
				IProblem.UnusedPrivateConstructor,
			new String[] {
				new String(constructor.declaringClass.readableName()),
				typesAsString(constructor, false)
			 },
			new String[] {
				new String(constructor.declaringClass.shortReadableName()),
				typesAsString(constructor, true)
			 },
			severity,
			constructorDecl.sourceStart,
			constructorDecl.sourceEnd);
	}
	public void unusedPrivateField(FieldDeclaration fieldDecl) {
	
		int severity = computeSeverity(IProblem.UnusedPrivateField);
		if (severity == ProblemSeverities.Ignore) return;
	
		FieldBinding field = fieldDecl.binding;
	
		if (CharOperation.equals(TypeConstants.SERIALVERSIONUID, field.name)
				&& field.isStatic()
				&& field.isFinal()
				&& TypeBinding.equalsEquals(TypeBinding.LONG, field.type)) {
			ReferenceBinding referenceBinding = field.declaringClass;
			if (referenceBinding != null) {
				if (referenceBinding.findSuperTypeOriginatingFrom(TypeIds.T_JavaIoSerializable, false /*Serializable is not a class*/) != null) {
					return; // do not report unused serialVersionUID field for class that implements Serializable
				}
			}
		}
		if (CharOperation.equals(TypeConstants.SERIALPERSISTENTFIELDS, field.name)
				&& field.isStatic()
				&& field.isFinal()
				&& field.type.dimensions() == 1
				&& CharOperation.equals(TypeConstants.CharArray_JAVA_IO_OBJECTSTREAMFIELD, field.type.leafComponentType().readableName())) {
			ReferenceBinding referenceBinding = field.declaringClass;
			if (referenceBinding != null) {
				if (referenceBinding.findSuperTypeOriginatingFrom(TypeIds.T_JavaIoSerializable, false /*Serializable is not a class*/) != null) {
					return; // do not report unused serialVersionUID field for class that implements Serializable
				}
			}
		}
		if (excludeDueToAnnotation(fieldDecl.annotations, IProblem.UnusedPrivateField)) return;
		this.handle(
				IProblem.UnusedPrivateField,
			new String[] {
				new String(field.declaringClass.readableName()),
				new String(field.name),
			 },
			new String[] {
				new String(field.declaringClass.shortReadableName()),
				new String(field.name),
			 },
			severity,
			nodeSourceStart(field, fieldDecl),
			nodeSourceEnd(field, fieldDecl));
	}
	public void unusedPrivateMethod(AbstractMethodDeclaration methodDecl) {
	
		int severity = computeSeverity(IProblem.UnusedPrivateMethod);
		if (severity == ProblemSeverities.Ignore) return;
	
		MethodBinding method = methodDecl.binding;
	
		// no report for serialization support 'void readObject(ObjectInputStream)'
		if (!method.isStatic()
				&& TypeBinding.VOID == method.returnType
				&& method.parameters.length == 1
				&& method.parameters[0].dimensions() == 0
				&& CharOperation.equals(method.selector, TypeConstants.READOBJECT)
				&& CharOperation.equals(TypeConstants.CharArray_JAVA_IO_OBJECTINPUTSTREAM, method.parameters[0].readableName())) {
			return;
		}
		// no report for serialization support 'void writeObject(ObjectOutputStream)'
		if (!method.isStatic()
				&& TypeBinding.VOID == method.returnType
				&& method.parameters.length == 1
				&& method.parameters[0].dimensions() == 0
				&& CharOperation.equals(method.selector, TypeConstants.WRITEOBJECT)
				&& CharOperation.equals(TypeConstants.CharArray_JAVA_IO_OBJECTOUTPUTSTREAM, method.parameters[0].readableName())) {
			return;
		}
		// no report for serialization support 'Object readResolve()'
		if (!method.isStatic()
				&& TypeIds.T_JavaLangObject == method.returnType.id
				&& method.parameters.length == 0
				&& CharOperation.equals(method.selector, TypeConstants.READRESOLVE)) {
			return;
		}
		// no report for serialization support 'Object writeReplace()'
		if (!method.isStatic()
				&& TypeIds.T_JavaLangObject == method.returnType.id
				&& method.parameters.length == 0
				&& CharOperation.equals(method.selector, TypeConstants.WRITEREPLACE)) {
			return;
		}
		if (excludeDueToAnnotation(methodDecl.annotations, IProblem.UnusedPrivateMethod)) return;
		
		this.handle(
				IProblem.UnusedPrivateMethod,
			new String[] {
				new String(method.declaringClass.readableName()),
				new String(method.selector),
				typesAsString(method, false)
			 },
			new String[] {
				new String(method.declaringClass.shortReadableName()),
				new String(method.selector),
				typesAsString(method, true)
			 },
			severity,
			methodDecl.sourceStart,
			methodDecl.sourceEnd);
	}
	
	/**
	 * Returns true if a private member should not be warned as unused if
	 * annotated with a non-standard annotation.
	 * https://bugs.eclipse.org/365437
	 * https://bugs.eclipse.org/376590
	 */
	private boolean excludeDueToAnnotation(Annotation[] annotations, int problemId) {
		int annotationsLen = 0;
		if (annotations != null) {
			annotationsLen = annotations.length;
		} else {
			return false;
		}
		if (annotationsLen == 0) return false;
		for (int i = 0; i < annotationsLen; i++) {
			TypeBinding resolvedType = annotations[i].resolvedType;
			if (resolvedType != null) {
				switch (resolvedType.id) {
					case TypeIds.T_JavaLangSuppressWarnings:
					case TypeIds.T_JavaLangDeprecated:
					case TypeIds.T_JavaLangSafeVarargs:
					case TypeIds.T_ConfiguredAnnotationNonNull:
					case TypeIds.T_ConfiguredAnnotationNullable:
					case TypeIds.T_ConfiguredAnnotationNonNullByDefault:
						break;
					case TypeIds.T_JavaxInjectInject:
					case TypeIds.T_ComGoogleInjectInject:
						if (problemId != IProblem.UnusedPrivateField)
							return true; // @Inject on method/ctor does constitute a relevant use, just on fields it doesn't
						break;
					default:
						// non-standard annotation found, don't warn
						return true;
				}
			}
		}
		return false;
	}
	public void unusedPrivateType(TypeDeclaration typeDecl) {
		int severity = computeSeverity(IProblem.UnusedPrivateType);
		if (severity == ProblemSeverities.Ignore) return;
		if (excludeDueToAnnotation(typeDecl.annotations, IProblem.UnusedPrivateType)) return;
		ReferenceBinding type = typeDecl.binding;
		this.handle(
				IProblem.UnusedPrivateType,
			new String[] {
				new String(type.readableName()),
			 },
			new String[] {
				new String(type.shortReadableName()),
			 },
			severity,
			typeDecl.sourceStart,
			typeDecl.sourceEnd);
	}
	public void unusedTypeParameter(TypeParameter typeParameter) {
		int severity = computeSeverity(IProblem.UnusedTypeParameter);
		if (severity == ProblemSeverities.Ignore) return;
		String [] arguments = new String[] {new String(typeParameter.name)};
		this.handle(
				IProblem.UnusedTypeParameter,
				arguments,
				arguments,
				typeParameter.sourceStart,
				typeParameter.sourceEnd);
	}
	public void unusedWarningToken(Expression token) {
		String[] arguments = new String[] { token.constant.stringValue() };
		this.handle(
			IProblem.UnusedWarningToken,
			arguments,
			arguments,
			token.sourceStart,
			token.sourceEnd);
	}
	public void useAssertAsAnIdentifier(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.UseAssertAsAnIdentifier,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void useEnumAsAnIdentifier(int sourceStart, int sourceEnd) {
		this.handle(
			IProblem.UseEnumAsAnIdentifier,
			NoArgument,
			NoArgument,
			sourceStart,
			sourceEnd);
	}
	public void illegalUseOfUnderscoreAsAnIdentifier(int sourceStart, int sourceEnd, boolean lambdaParameter) {
		this.underScoreIsLambdaParameter = lambdaParameter;
		try {
			this.handle(
				IProblem.IllegalUseOfUnderscoreAsAnIdentifier,
				NoArgument,
				NoArgument,
				sourceStart,
				sourceEnd);
		} finally {
			this.underScoreIsLambdaParameter = false;	
		}
	}
	public void varargsArgumentNeedCast(MethodBinding method, TypeBinding argumentType, InvocationSite location) {
		int severity = this.options.getSeverity(CompilerOptions.VarargsArgumentNeedCast);
		if (severity == ProblemSeverities.Ignore) return;
		ArrayBinding varargsType = (ArrayBinding)method.parameters[method.parameters.length-1];
		if (method.isConstructor()) {
			this.handle(
				IProblem.ConstructorVarargsArgumentNeedCast,
				new String[] {
						new String(argumentType.readableName()),
						new String(varargsType.readableName()),
						new String(method.declaringClass.readableName()),
						typesAsString(method, false),
						new String(varargsType.elementsType().readableName()),
				},
				new String[] {
						new String(argumentType.shortReadableName()),
						new String(varargsType.shortReadableName()),
						new String(method.declaringClass.shortReadableName()),
						typesAsString(method, true),
						new String(varargsType.elementsType().shortReadableName()),
				},
				severity,
				location.sourceStart(),
				location.sourceEnd());
		} else {
			this.handle(
				IProblem.MethodVarargsArgumentNeedCast,
				new String[] {
						new String(argumentType.readableName()),
						new String(varargsType.readableName()),
						new String(method.selector),
						typesAsString(method, false),
						new String(method.declaringClass.readableName()),
						new String(varargsType.elementsType().readableName()),
				},
				new String[] {
						new String(argumentType.shortReadableName()),
						new String(varargsType.shortReadableName()),
						new String(method.selector), typesAsString(method, true),
						new String(method.declaringClass.shortReadableName()),
						new String(varargsType.elementsType().shortReadableName()),
				},
				severity,
				location.sourceStart(),
				location.sourceEnd());
		}
	}
	public void varargsConflict(MethodBinding method1, MethodBinding method2, SourceTypeBinding type) {
		this.handle(
			IProblem.VarargsConflict,
			new String[] {
			        new String(method1.selector),
			        typesAsString(method1, false),
			        new String(method1.declaringClass.readableName()),
			        typesAsString(method2, false),
			        new String(method2.declaringClass.readableName())
			},
			new String[] {
			        new String(method1.selector),
			        typesAsString(method1, true),
			        new String(method1.declaringClass.shortReadableName()),
			        typesAsString(method2, true),
			        new String(method2.declaringClass.shortReadableName())
			},
			TypeBinding.equalsEquals(method1.declaringClass, type) ? method1.sourceStart() : type.sourceStart(),
			TypeBinding.equalsEquals(method1.declaringClass, type) ? method1.sourceEnd() : type.sourceEnd());
	}
	public void safeVarargsOnFixedArityMethod(MethodBinding method) {
		String [] arguments = new String[] { new String(method.isConstructor() ? method.declaringClass.shortReadableName() : method.selector)}; 
		this.handle(
			IProblem.SafeVarargsOnFixedArityMethod,
			arguments,
			arguments,
			method.sourceStart(),
			method.sourceEnd());
	}
	public void safeVarargsOnNonFinalInstanceMethod(MethodBinding method) {
		String [] arguments = new String[] { new String(method.isConstructor() ? method.declaringClass.shortReadableName() : method.selector)}; 
		this.handle(
			IProblem.SafeVarargsOnNonFinalInstanceMethod,
			arguments,
			arguments,
			method.sourceStart(),
			method.sourceEnd());
	}
	public void possibleHeapPollutionFromVararg(AbstractVariableDeclaration vararg) {
		String[] arguments = new String[] {new String(vararg.name)};
		this.handle(
			IProblem.PotentialHeapPollutionFromVararg,
			arguments,
			arguments,
			vararg.sourceStart,
			vararg.sourceEnd);
	}
	public void variableTypeCannotBeVoid(AbstractVariableDeclaration varDecl) {
		String[] arguments = new String[] {new String(varDecl.name)};
		this.handle(
			IProblem.VariableTypeCannotBeVoid,
			arguments,
			arguments,
			varDecl.sourceStart,
			varDecl.sourceEnd);
	}
	public void variableTypeCannotBeVoidArray(AbstractVariableDeclaration varDecl) {
		this.handle(
			IProblem.CannotAllocateVoidArray,
			NoArgument,
			NoArgument,
			varDecl.type.sourceStart,
			varDecl.type.sourceEnd);
	}
	public void visibilityConflict(MethodBinding currentMethod, MethodBinding inheritedMethod) {
		this.handle(
			//	Cannot reduce the visibility of the inherited method from %1
			// 8.4.6.3 - The access modifier of an hiding method must provide at least as much access as the hidden method.
			// 8.4.6.3 - The access modifier of an overiding method must provide at least as much access as the overriden method.
			IProblem.MethodReducesVisibility,
			new String[] {new String(inheritedMethod.declaringClass.readableName())},
			new String[] {new String(inheritedMethod.declaringClass.shortReadableName())},
			currentMethod.sourceStart(),
			currentMethod.sourceEnd());
	}
	public void wildcardAssignment(TypeBinding variableType, TypeBinding expressionType, ASTNode location) {
		this.handle(
			IProblem.WildcardFieldAssignment,
			new String[] {
			        new String(expressionType.readableName()), new String(variableType.readableName()) },
			new String[] {
			        new String(expressionType.shortReadableName()), new String(variableType.shortReadableName()) },
			location.sourceStart,
			location.sourceEnd);
	}
	public void wildcardInvocation(ASTNode location, TypeBinding receiverType, MethodBinding method, TypeBinding[] arguments) {
		TypeBinding offendingArgument = null;
		TypeBinding offendingParameter = null;
		for (int i = 0, length = method.parameters.length; i < length; i++) {
			TypeBinding parameter = method.parameters[i];
			if (parameter.isWildcard() && (((WildcardBinding) parameter).boundKind != Wildcard.SUPER)) {
				offendingParameter = parameter;
				offendingArgument = arguments[i];
				break;
			}
		}
	
		if (method.isConstructor()) {
			this.handle(
				IProblem.WildcardConstructorInvocation,
				new String[] {
					new String(receiverType.sourceName()),
					typesAsString(method, false),
					new String(receiverType.readableName()),
					typesAsString(arguments, false),
					new String(offendingArgument.readableName()),
					new String(offendingParameter.readableName()),
				 },
				new String[] {
					new String(receiverType.sourceName()),
					typesAsString(method, true),
					new String(receiverType.shortReadableName()),
					typesAsString(arguments, true),
					new String(offendingArgument.shortReadableName()),
					new String(offendingParameter.shortReadableName()),
				 },
				location.sourceStart,
				location.sourceEnd);
	    } else {
			this.handle(
				IProblem.WildcardMethodInvocation,
				new String[] {
					new String(method.selector),
					typesAsString(method, false),
					new String(receiverType.readableName()),
					typesAsString(arguments, false),
					new String(offendingArgument.readableName()),
					new String(offendingParameter.readableName()),
				 },
				new String[] {
					new String(method.selector),
					typesAsString(method, true),
					new String(receiverType.shortReadableName()),
					typesAsString(arguments, true),
					new String(offendingArgument.shortReadableName()),
					new String(offendingParameter.shortReadableName()),
				 },
				location.sourceStart,
				location.sourceEnd);
	    }
	}
	public void wrongSequenceOfExceptionTypesError(TypeReference typeRef, TypeBinding exceptionType, TypeBinding hidingExceptionType) {
		//the two catch block under and upper are in an incorrect order.
		//under should be define BEFORE upper in the source
	
		this.handle(
			IProblem.InvalidCatchBlockSequence,
			new String[] {
				new String(exceptionType.readableName()),
				new String(hidingExceptionType.readableName()),
			 },
			new String[] {
				new String(exceptionType.shortReadableName()),
				new String(hidingExceptionType.shortReadableName()),
			 },
			typeRef.sourceStart,
			typeRef.sourceEnd);
	}
	public void wrongSequenceOfExceptionTypes(TypeReference typeRef, TypeBinding exceptionType, TypeBinding hidingExceptionType) {
		// type references inside a multi-catch block are not of union type
		this.handle(
			IProblem.InvalidUnionTypeReferenceSequence,
			new String[] {
				new String(exceptionType.readableName()),
				new String(hidingExceptionType.readableName()),
			 },
			new String[] {
				new String(exceptionType.shortReadableName()),
				new String(hidingExceptionType.shortReadableName()),
			 },
			typeRef.sourceStart,
			typeRef.sourceEnd);
	}
	
	public void autoManagedResourcesNotBelow17(LocalDeclaration[] resources) {
		this.handle(
				IProblem.AutoManagedResourceNotBelow17,
				NoArgument,
				NoArgument,
				resources[0].declarationSourceStart,
				resources[resources.length - 1].declarationSourceEnd);
	}
	public void cannotInferElidedTypes(AllocationExpression allocationExpression) {
		String arguments [] = new String [] { allocationExpression.type.toString() };
		this.handle(
				IProblem.CannotInferElidedTypes,
				arguments,
				arguments,
				allocationExpression.sourceStart, 
				allocationExpression.sourceEnd);
	}
	public void diamondNotWithExplicitTypeArguments(TypeReference[] typeArguments) {
		this.handle(
				IProblem.CannotUseDiamondWithExplicitTypeArguments,
				NoArgument,
				NoArgument,
				typeArguments[0].sourceStart, 
				typeArguments[typeArguments.length - 1].sourceEnd);
	}
	public void rawConstructorReferenceNotWithExplicitTypeArguments(TypeReference[] typeArguments) {
		this.handle(
				IProblem.IllegalTypeArgumentsInRawConstructorReference,
				NoArgument,
				NoArgument,
				typeArguments[0].sourceStart, 
				typeArguments[typeArguments.length - 1].sourceEnd);
	}
	public void diamondNotWithAnoymousClasses(TypeReference type) {
		this.handle(
				IProblem.CannotUseDiamondWithAnonymousClasses,
				NoArgument,
				NoArgument,
				type.sourceStart, 
				type.sourceEnd);
	}
	public void redundantSpecificationOfTypeArguments(ASTNode location, TypeBinding[] argumentTypes) {
		int severity = computeSeverity(IProblem.RedundantSpecificationOfTypeArguments);
		if (severity != ProblemSeverities.Ignore) {
			int sourceStart = -1;
			if (location instanceof QualifiedTypeReference) {
				QualifiedTypeReference ref = (QualifiedTypeReference)location;
				sourceStart = (int) (ref.sourcePositions[ref.sourcePositions.length - 1] >> 32);
			} else {
				sourceStart = location.sourceStart;
			}
			this.handle(
				IProblem.RedundantSpecificationOfTypeArguments,
				new String[] {typesAsString(argumentTypes, false)},
				new String[] {typesAsString(argumentTypes, true)},
				severity,
				sourceStart,
				location.sourceEnd);
	    }
	}
	public void potentiallyUnclosedCloseable(FakedTrackingVariable trackVar, ASTNode location) {
		String[] args = { trackVar.nameForReporting(location, this.referenceContext) };
		if (location == null) {
			this.handle(
				IProblem.PotentiallyUnclosedCloseable,
				args,
				args,
				trackVar.sourceStart,
				trackVar.sourceEnd);
		} else {
			this.handle(
				IProblem.PotentiallyUnclosedCloseableAtExit,
				args,
				args,
				location.sourceStart,
				location.sourceEnd);
		}
	}
	public void unclosedCloseable(FakedTrackingVariable trackVar, ASTNode location) {
		String[] args = { String.valueOf(trackVar.name) };
		if (location == null) {
			this.handle(
				IProblem.UnclosedCloseable,
				args,
				args,
				trackVar.sourceStart,
				trackVar.sourceEnd);
		} else {
			this.handle(
				IProblem.UnclosedCloseableAtExit,
				args,
				args,
				location.sourceStart,
				location.sourceEnd);
		}
	}
	public void explicitlyClosedAutoCloseable(FakedTrackingVariable trackVar) {
		String[] args = { String.valueOf(trackVar.name) };
		this.handle(
			IProblem.ExplicitlyClosedAutoCloseable,
			args,
			args,
			trackVar.sourceStart,
			trackVar.sourceEnd);	
	}
	
	public void nullityMismatch(Expression expression, TypeBinding providedType, TypeBinding requiredType, int nullStatus, char[][] annotationName) {
		if ((nullStatus & FlowInfo.NULL) != 0) {
			nullityMismatchIsNull(expression, requiredType);
			return;
		}
		if (expression instanceof MessageSend) {
			if ((((MessageSend) expression).binding.tagBits & TagBits.AnnotationNullable) != 0) {
				nullityMismatchSpecdNullable(expression, requiredType, this.options.nonNullAnnotationName);
				return;
			}
		}
		if ((nullStatus & FlowInfo.POTENTIALLY_NULL) != 0) {
			VariableBinding var = expression.localVariableBinding();
			if (var == null && expression instanceof Reference) {
				var = ((Reference)expression).lastFieldBinding();
			}
			if (var != null && var.isNullable()) {
				nullityMismatchSpecdNullable(expression, requiredType, annotationName);
				return;
			}
			nullityMismatchPotentiallyNull(expression, requiredType, annotationName);
			return;
		}
		if (this.options.sourceLevel < ClassFileConstants.JDK1_8)
			nullityMismatchIsUnknown(expression, providedType, requiredType, annotationName);
		else
			nullityMismatchingTypeAnnotation(expression, providedType, requiredType, NullAnnotationMatching.NULL_ANNOTATIONS_UNCHECKED);
	}
	public void nullityMismatchIsNull(Expression expression, TypeBinding requiredType) {
		int problemId = IProblem.RequiredNonNullButProvidedNull;
		boolean below18 = this.options.sourceLevel < ClassFileConstants.JDK1_8;
		if (!below18 && requiredType.isTypeVariable() && !requiredType.hasNullTypeAnnotations())
			problemId = IProblem.NullNotCompatibleToFreeTypeVariable;
		if (requiredType instanceof CaptureBinding) {
			CaptureBinding capture = (CaptureBinding) requiredType;
			if (capture.wildcard != null)
				requiredType = capture.wildcard;
		}
		String[] arguments;
		String[] argumentsShort;
		if (below18) {
			arguments      = new String[] { annotatedTypeName(requiredType, this.options.nonNullAnnotationName) };
			argumentsShort = new String[] { shortAnnotatedTypeName(requiredType, this.options.nonNullAnnotationName) };
		} else {
			if (problemId == IProblem.NullNotCompatibleToFreeTypeVariable) {
				arguments      = new String[] { new String(requiredType.sourceName()) }; // don't show any bounds
				argumentsShort = new String[] { new String(requiredType.sourceName()) };
			} else {
				arguments      = new String[] { new String(requiredType.nullAnnotatedReadableName(this.options, false)) };
				argumentsShort = new String[] { new String(requiredType.nullAnnotatedReadableName(this.options, true))  };			
			}
		}
		this.handle(problemId, arguments, argumentsShort, expression.sourceStart, expression.sourceEnd);
	}
	public void nullityMismatchSpecdNullable(Expression expression, TypeBinding requiredType, char[][] annotationName) {
		int problemId = IProblem.RequiredNonNullButProvidedSpecdNullable;
		char[][] nullableName = this.options.nullableAnnotationName;
		String[] arguments = new String[] {
				annotatedTypeName(requiredType, annotationName),
				String.valueOf(CharOperation.concatWith(nullableName, '.'))
		};
		String[] argumentsShort = new String[] {
				shortAnnotatedTypeName(requiredType, annotationName),
				String.valueOf(nullableName[nullableName.length-1])
		};
		this.handle(problemId, arguments, argumentsShort, expression.sourceStart, expression.sourceEnd);
	}
	public void nullityMismatchPotentiallyNull(Expression expression, TypeBinding requiredType, char[][] annotationName) {
		int problemId = IProblem.RequiredNonNullButProvidedPotentialNull;
		char[][] nullableName = this.options.nullableAnnotationName;
		String[] arguments = new String[] {
				annotatedTypeName(requiredType, annotationName),
				String.valueOf(CharOperation.concatWith(nullableName, '.'))
		};
		String[] argumentsShort = new String[] {
				shortAnnotatedTypeName(requiredType, annotationName),
				String.valueOf(nullableName[nullableName.length-1])
		};
		this.handle(problemId, arguments, argumentsShort, expression.sourceStart, expression.sourceEnd);
	}
	public void nullityMismatchIsUnknown(Expression expression, TypeBinding providedType, TypeBinding requiredType, char[][] annotationName) {
		int problemId = IProblem.RequiredNonNullButProvidedUnknown;
		String[] arguments = new String[] {
				String.valueOf(providedType.readableName()),
				annotatedTypeName(requiredType, annotationName)
		};
		String[] argumentsShort = new String[] {
				String.valueOf(providedType.shortReadableName()),
				shortAnnotatedTypeName(requiredType, annotationName)
		};
		this.handle(problemId, arguments, argumentsShort, expression.sourceStart, expression.sourceEnd);
	}
	public void illegalRedefinitionToNonNullParameter(Argument argument, ReferenceBinding declaringClass, char[][] inheritedAnnotationName) {
		int sourceStart = argument.type.sourceStart;
		if (argument.annotations != null) {
			for (int i=0; i<argument.annotations.length; i++) {
				Annotation annotation = argument.annotations[i];
				if (   annotation.resolvedType.id == TypeIds.T_ConfiguredAnnotationNullable
					|| annotation.resolvedType.id == TypeIds.T_ConfiguredAnnotationNonNull)
				{
					sourceStart = annotation.sourceStart;
					break;
				}
			}
		}
		if (inheritedAnnotationName == null) {
			this.handle(
				IProblem.IllegalDefinitionToNonNullParameter, 
				new String[] { new String(argument.name), new String(declaringClass.readableName()) },
				new String[] { new String(argument.name), new String(declaringClass.shortReadableName()) },
				sourceStart,
				argument.type.sourceEnd);
		} else {
			this.handle(
				IProblem.IllegalRedefinitionToNonNullParameter, 
				new String[] { new String(argument.name), new String(declaringClass.readableName()), CharOperation.toString(inheritedAnnotationName)},
				new String[] { new String(argument.name), new String(declaringClass.shortReadableName()), new String(inheritedAnnotationName[inheritedAnnotationName.length-1])},
				sourceStart,
				argument.type.sourceEnd);
		}
	}
	public void parameterLackingNullableAnnotation(Argument argument, ReferenceBinding declaringClass, char[][] inheritedAnnotationName) {
		this.handle(
			IProblem.ParameterLackingNullableAnnotation, 
			new String[] { new String(declaringClass.readableName()), CharOperation.toString(inheritedAnnotationName)},
			new String[] { new String(declaringClass.shortReadableName()), new String(inheritedAnnotationName[inheritedAnnotationName.length-1])},
			argument.type.sourceStart,
			argument.type.sourceEnd);
	}
	public void parameterLackingNonnullAnnotation(Argument argument, ReferenceBinding declaringClass, char[][] inheritedAnnotationName) {
		int sourceStart = 0, sourceEnd = 0;
		if (argument != null) {
			sourceStart = argument.type.sourceStart;
			sourceEnd = argument.type.sourceEnd;
		} else if (this.referenceContext instanceof TypeDeclaration) {
			sourceStart = ((TypeDeclaration) this.referenceContext).sourceStart;
			sourceEnd =   ((TypeDeclaration) this.referenceContext).sourceEnd;
		}
		this.handle(
			IProblem.ParameterLackingNonNullAnnotation, 
			new String[] { new String(declaringClass.readableName()), CharOperation.toString(inheritedAnnotationName)},
			new String[] { new String(declaringClass.shortReadableName()), new String(inheritedAnnotationName[inheritedAnnotationName.length-1])},
			sourceStart,
			sourceEnd);
	}
	public void illegalReturnRedefinition(AbstractMethodDeclaration abstractMethodDecl, MethodBinding inheritedMethod, char[][] nonNullAnnotationName) {
		MethodDeclaration methodDecl = (MethodDeclaration) abstractMethodDecl;
		StringBuffer methodSignature = new StringBuffer();
		methodSignature
			.append(inheritedMethod.declaringClass.readableName())
			.append('.')
			.append(inheritedMethod.readableName());
	
		StringBuffer shortSignature = new StringBuffer();
		shortSignature
			.append(inheritedMethod.declaringClass.shortReadableName())
			.append('.')
			.append(inheritedMethod.shortReadableName());
		int sourceStart = methodDecl.returnType.sourceStart;
		Annotation[] annotations = methodDecl.annotations;
		Annotation annotation = findAnnotation(annotations, TypeIds.T_ConfiguredAnnotationNullable);
		if (annotation != null) {
			sourceStart = annotation.sourceStart;
		}
		this.handle(
			IProblem.IllegalReturnNullityRedefinition, 
			new String[] { methodSignature.toString(), CharOperation.toString(nonNullAnnotationName)},
			new String[] { shortSignature.toString(), new String(nonNullAnnotationName[nonNullAnnotationName.length-1])},
			sourceStart,
			methodDecl.returnType.sourceEnd);
	}
	public void referenceExpressionArgumentNullityMismatch(ReferenceExpression location, TypeBinding requiredType, TypeBinding providedType,
			MethodBinding descriptorMethod, int idx, NullAnnotationMatching status) {
		StringBuffer methodSignature = new StringBuffer();
		methodSignature
			.append(descriptorMethod.declaringClass.readableName())
			.append('.')
			.append(descriptorMethod.readableName());
		StringBuffer shortSignature = new StringBuffer();
		shortSignature
			.append(descriptorMethod.declaringClass.shortReadableName())
			.append('.')
			.append(descriptorMethod.shortReadableName());
		this.handle(
				status.isUnchecked() ? IProblem.ReferenceExpressionParameterNullityUnchecked : IProblem.ReferenceExpressionParameterNullityMismatch,
				new String[] { String.valueOf(idx+1), 
								String.valueOf(requiredType.nullAnnotatedReadableName(this.options, false)),
								String.valueOf(providedType.nullAnnotatedReadableName(this.options, false)),
								methodSignature.toString() },
				new String[] { String.valueOf(idx+1), 
								String.valueOf(requiredType.nullAnnotatedReadableName(this.options, true)),
								String.valueOf(providedType.nullAnnotatedReadableName(this.options, true)),
								shortSignature.toString() },
				location.sourceStart,
				location.sourceEnd);
	}
	public void illegalReturnRedefinition(ASTNode location, MethodBinding descriptorMethod,
				char[][] nonNullAnnotationName, 
				char/*@Nullable*/[][] providedAnnotationName, TypeBinding providedType) {
		StringBuffer methodSignature = new StringBuffer()
			.append(descriptorMethod.declaringClass.readableName())
			.append('.')
			.append(descriptorMethod.readableName());
		StringBuffer shortSignature = new StringBuffer()
			.append(descriptorMethod.declaringClass.shortReadableName())
			.append('.')
			.append(descriptorMethod.shortReadableName());
		StringBuffer providedPrefix = new StringBuffer(); 
		StringBuffer providedShortPrefix = new StringBuffer(); 
		if (providedAnnotationName != null) {
			providedPrefix.append('@').append(CharOperation.toString(providedAnnotationName)).append(' ');
			providedShortPrefix.append('@').append(providedAnnotationName[providedAnnotationName.length-1]).append(' ');
		}
		this.handle(
			providedAnnotationName == null
				? IProblem.ReferenceExpressionReturnNullRedefUnchecked
				: IProblem.ReferenceExpressionReturnNullRedef,
			new String[] { methodSignature.toString(),
							CharOperation.toString(nonNullAnnotationName), String.valueOf(descriptorMethod.returnType.readableName()),
							providedPrefix.toString(), String.valueOf(providedType.readableName())},
			new String[] { shortSignature.toString(),
							String.valueOf(nonNullAnnotationName[nonNullAnnotationName.length-1]), String.valueOf(descriptorMethod.returnType.shortReadableName()),
							providedShortPrefix.toString(), String.valueOf(providedType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	public void messageSendPotentialNullReference(MethodBinding method, ASTNode location) {
		String[] arguments = new String[] {new String(method.readableName())};
		this.handle(
			IProblem.PotentialNullMessageSendReference,
			arguments,
			arguments,
			location.sourceStart,
			location.sourceEnd);
	}
	public void messageSendRedundantCheckOnNonNull(MethodBinding method, ASTNode location) {
		String[] arguments = new String[] {new String(method.readableName())  };
		this.handle(
			IProblem.RedundantNullCheckOnNonNullMessageSend,
			arguments,
			arguments,
			location.sourceStart,
			location.sourceEnd);
	}
	public void expressionNullReference(ASTNode location) {
		this.handle(
			IProblem.NullExpressionReference,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	public void expressionPotentialNullReference(ASTNode location) {
		this.handle(
			IProblem.PotentialNullExpressionReference,
			NoArgument,
			NoArgument,
			location.sourceStart,
			location.sourceEnd);
	}
	
	public void cannotImplementIncompatibleNullness(MethodBinding currentMethod, MethodBinding inheritedMethod, boolean showReturn) {
		int sourceStart = 0, sourceEnd = 0;
		if (this.referenceContext instanceof TypeDeclaration) {
			sourceStart = ((TypeDeclaration) this.referenceContext).sourceStart;
			sourceEnd =   ((TypeDeclaration) this.referenceContext).sourceEnd;
		}
		String[] problemArguments = {
				showReturn 
					? new String(currentMethod.returnType.nullAnnotatedReadableName(this.options, false))+' '
					: "", //$NON-NLS-1$
				new String(currentMethod.selector),
				typesAsString(currentMethod, false, true),
				new String(currentMethod.declaringClass.readableName()),
				new String(inheritedMethod.declaringClass.readableName())
			};
		String[] messageArguments = {
				showReturn 
					? new String(currentMethod.returnType.nullAnnotatedReadableName(this.options, true))+' '
					: "", //$NON-NLS-1$
				new String(currentMethod.selector),
				typesAsString(currentMethod, true, true),
				new String(currentMethod.declaringClass.shortReadableName()),
				new String(inheritedMethod.declaringClass.shortReadableName())
			};
		this.handle(
				IProblem.CannotImplementIncompatibleNullness,
				problemArguments,
				messageArguments,
				sourceStart,
				sourceEnd);
	}
	
	public void nullAnnotationIsRedundant(AbstractMethodDeclaration sourceMethod, int i) {
		int sourceStart, sourceEnd;
		if (i == -1) {
			MethodDeclaration methodDecl = (MethodDeclaration) sourceMethod;
			Annotation annotation = findAnnotation(methodDecl.annotations, TypeIds.T_ConfiguredAnnotationNonNull);
			sourceStart = annotation != null ? annotation.sourceStart : methodDecl.returnType.sourceStart;
			sourceEnd = methodDecl.returnType.sourceEnd;
		} else {
			Argument arg = sourceMethod.arguments[i];
			sourceStart = arg.declarationSourceStart;
			sourceEnd = arg.sourceEnd;
		}
		this.handle(IProblem.RedundantNullAnnotation, ProblemHandler.NoArgument, ProblemHandler.NoArgument, sourceStart, sourceEnd);
	}
	
	public void nullAnnotationIsRedundant(FieldDeclaration sourceField) {
		Annotation annotation = findAnnotation(sourceField.annotations, TypeIds.T_ConfiguredAnnotationNonNull);
		int sourceStart = annotation != null ? annotation.sourceStart : sourceField.type.sourceStart;
		int sourceEnd = sourceField.type.sourceEnd;
		this.handle(IProblem.RedundantNullAnnotation, ProblemHandler.NoArgument, ProblemHandler.NoArgument, sourceStart, sourceEnd);
	}
	
	public void nullDefaultAnnotationIsRedundant(ASTNode location, Annotation[] annotations, Binding outer) {
		Annotation annotation = findAnnotation(annotations, TypeIds.T_ConfiguredAnnotationNonNullByDefault);
		int start = annotation != null ? annotation.sourceStart : location.sourceStart;
		int end = annotation != null ? annotation.sourceEnd : location.sourceStart;
		String[] args = NoArgument;
		String[] shortArgs = NoArgument;
		if (outer != null) {
			args = new String[] { new String(outer.readableName()) };
			shortArgs = new String[] { new String(outer.shortReadableName()) };
		}
		int problemId = IProblem.RedundantNullDefaultAnnotation;
		if (outer instanceof PackageBinding) {
			problemId = IProblem.RedundantNullDefaultAnnotationPackage;
		} else if (outer instanceof ReferenceBinding) {
			problemId = IProblem.RedundantNullDefaultAnnotationType;
		} else if (outer instanceof MethodBinding) {
			problemId = IProblem.RedundantNullDefaultAnnotationMethod;
		}
		this.handle(problemId, args, shortArgs, start, end);
	}
	
	public void contradictoryNullAnnotations(Annotation annotation) {
		contradictoryNullAnnotations(annotation.sourceStart, annotation.sourceEnd);
	}
	
	public void contradictoryNullAnnotations(Annotation[] annotations) {
		contradictoryNullAnnotations(annotations[0].sourceStart, annotations[annotations.length-1].sourceEnd);
	}
	
	public void contradictoryNullAnnotations(int sourceStart, int sourceEnd) {
		// when this error is triggered we can safely assume that both annotations have been configured
		char[][] nonNullAnnotationName = this.options.nonNullAnnotationName;
		char[][] nullableAnnotationName = this.options.nullableAnnotationName;
		String[] arguments = {
			new String(CharOperation.concatWith(nonNullAnnotationName, '.')),
			new String(CharOperation.concatWith(nullableAnnotationName, '.'))
		};
		String[] shortArguments = {
				new String(nonNullAnnotationName[nonNullAnnotationName.length-1]),
				new String(nullableAnnotationName[nullableAnnotationName.length-1])
			};
		this.handle(IProblem.ContradictoryNullAnnotations, arguments, shortArguments, sourceStart, sourceEnd);
	}
	
	public void contradictoryNullAnnotationsInferred(MethodBinding inferredMethod, ASTNode location) {
		contradictoryNullAnnotationsInferred(inferredMethod, location.sourceStart, location.sourceEnd);
	}
	public void contradictoryNullAnnotationsInferred(MethodBinding inferredMethod, InvocationSite location) {
		contradictoryNullAnnotationsInferred(inferredMethod, location.sourceStart(), location.sourceEnd());
	}
	public void contradictoryNullAnnotationsInferred(MethodBinding inferredMethod, int sourceStart, int sourceEnd) {
		// when this error is triggered we can safely assume that both annotations have been configured
		char[][] nonNullAnnotationName = this.options.nonNullAnnotationName;
		char[][] nullableAnnotationName = this.options.nullableAnnotationName;
		String[] arguments = {
			new String(CharOperation.concatWith(nonNullAnnotationName, '.')),
			new String(CharOperation.concatWith(nullableAnnotationName, '.')),
			new String(inferredMethod.returnType.nullAnnotatedReadableName(this.options, false)),
			new String(inferredMethod.selector),
			typesAsString(inferredMethod, false, true)
		};
		String[] shortArguments = {
				new String(nonNullAnnotationName[nonNullAnnotationName.length-1]),
				new String(nullableAnnotationName[nullableAnnotationName.length-1]),
				new String(inferredMethod.returnType.nullAnnotatedReadableName(this.options, true)),
				new String(inferredMethod.selector),
				typesAsString(inferredMethod, true, true)
			};
		this.handle(IProblem.ContradictoryNullAnnotationsInferred, arguments, shortArguments, sourceStart, sourceEnd);
	}
	
	public void contradictoryNullAnnotationsOnBounds(Annotation annotation, long previousTagBit) {
		char[][] annotationName = previousTagBit == TagBits.AnnotationNonNull ? this.options.nonNullAnnotationName : this.options.nullableAnnotationName;
		String[] arguments = {
			new String(CharOperation.concatWith(annotationName, '.')),
		};
		String[] shortArguments = {
			new String(annotationName[annotationName.length-1]),
		};
		this.handle(IProblem.ContradictoryNullAnnotationsOnBound, arguments, shortArguments, annotation.sourceStart, annotation.sourceEnd);
	}
	
	// conflict default <-> inherited
	public void conflictingNullAnnotations(MethodBinding currentMethod, ASTNode location, MethodBinding inheritedMethod)
	{
		char[][] nonNullAnnotationName = this.options.nonNullAnnotationName;
		char[][] nullableAnnotationName = this.options.nullableAnnotationName;
		String[] arguments = {
			new String(CharOperation.concatWith(nonNullAnnotationName, '.')),
			new String(CharOperation.concatWith(nullableAnnotationName, '.')),
			new String(inheritedMethod.declaringClass.readableName())
		};
		String[] shortArguments = {
				new String(nonNullAnnotationName[nonNullAnnotationName.length-1]),
				new String(nullableAnnotationName[nullableAnnotationName.length-1]),
				new String(inheritedMethod.declaringClass.shortReadableName())
			};
		this.handle(IProblem.ConflictingNullAnnotations, arguments, shortArguments, location.sourceStart, location.sourceEnd);
	}
	
	// conflict between different inheriteds
	public void conflictingInheritedNullAnnotations(ASTNode location, boolean previousIsNonNull, MethodBinding previousInherited, boolean isNonNull, MethodBinding inheritedMethod)
	{
		char[][] previousAnnotationName = previousIsNonNull ? this.options.nonNullAnnotationName : this.options.nullableAnnotationName;
		char[][] annotationName = isNonNull ? this.options.nonNullAnnotationName : this.options.nullableAnnotationName;
		String[] arguments = {
			new String(CharOperation.concatWith(previousAnnotationName, '.')),
			new String(previousInherited.declaringClass.readableName()),
			new String(CharOperation.concatWith(annotationName, '.')),
			new String(inheritedMethod.declaringClass.readableName())
		};
		String[] shortArguments = {
				new String(previousAnnotationName[previousAnnotationName.length-1]),
				new String(previousInherited.declaringClass.shortReadableName()),
				new String(annotationName[annotationName.length-1]),
				new String(inheritedMethod.declaringClass.shortReadableName())
			};
		this.handle(IProblem.ConflictingInheritedNullAnnotations, arguments, shortArguments, location.sourceStart, location.sourceEnd);
	}
	
	public void illegalAnnotationForBaseType(TypeReference type, Annotation[] annotations, long nullAnnotationTagBit)
	{
		int typeId = (nullAnnotationTagBit == TagBits.AnnotationNullable) 
				? TypeIds.T_ConfiguredAnnotationNullable : TypeIds.T_ConfiguredAnnotationNonNull;
		char[][] annotationNames = (nullAnnotationTagBit == TagBits.AnnotationNonNull)
				? this.options.nonNullAnnotationName
				: this.options.nullableAnnotationName;
		String[] args = new String[] { new String(annotationNames[annotationNames.length-1]), new String(type.getLastToken()) };
		Annotation annotation = findAnnotation(annotations, typeId);
		int start = annotation != null ? annotation.sourceStart : type.sourceStart;
		int end = annotation != null ? annotation.sourceEnd : type.sourceEnd;
		this.handle(IProblem.IllegalAnnotationForBaseType,
				args,
				args,
				start,
				end);
	}
	
	public void illegalAnnotationForBaseType(Annotation annotation, TypeBinding type)
	{
		String[] args = new String[] {
			new String(annotation.resolvedType.shortReadableName()),
			new String(type.readableName())
		};
		this.handle(IProblem.IllegalAnnotationForBaseType,
				args,
				args,
				annotation.sourceStart,
				annotation.sourceEnd);
	}
	
	private String annotatedTypeName(TypeBinding type, char[][] annotationName) {
		if ((type.tagBits & TagBits.AnnotationNullMASK) != 0)
			return String.valueOf(type.nullAnnotatedReadableName(this.options, false));
		int dims = 0;
		char[] typeName = type.readableName();
		char[] annotationDisplayName = CharOperation.concatWith(annotationName, '.');
		return internalAnnotatedTypeName(annotationDisplayName, typeName, dims);
	}
	private String shortAnnotatedTypeName(TypeBinding type, char[][] annotationName) {
		if ((type.tagBits & TagBits.AnnotationNullMASK) != 0)
			return String.valueOf(type.nullAnnotatedReadableName(this.options, true));
		int dims = 0;
		char[] typeName = type.shortReadableName();
		char[] annotationDisplayName = annotationName[annotationName.length-1];
		return internalAnnotatedTypeName(annotationDisplayName, typeName, dims);
	}
	
	String internalAnnotatedTypeName(char[] annotationName, char[] typeName, int dims) {
		char[] fullName;
		if (dims > 0) {
			int plainLen = annotationName.length+typeName.length+2; // adding '@' and ' ' ...
			fullName = new char[plainLen+(2*dims)]; // ... and []* 
			System.arraycopy(typeName, 0, fullName, 0, typeName.length);
			fullName[typeName.length] = ' ';
			fullName[typeName.length+1] = '@';
			System.arraycopy(annotationName, 0, fullName, typeName.length+2, annotationName.length);
			for (int i=0; i<dims; i++) {
				fullName[plainLen+i] = '[';
				fullName[plainLen+i+1] = ']';
			}
		} else {
			fullName = new char[annotationName.length+typeName.length+2]; // adding '@' and ' ' 
			fullName[0] = '@';
			System.arraycopy(annotationName, 0, fullName, 1, annotationName.length);
			fullName[annotationName.length+1] = ' ';
			System.arraycopy(typeName, 0, fullName, annotationName.length+2, typeName.length);
		}
		return String.valueOf(fullName);
	}
	private Annotation findAnnotation(Annotation[] annotations, int typeId) {
		if (annotations != null) {
			// should have a @NonNull/@Nullable annotation, search for it:
			int length = annotations.length;
			for (int j=0; j<length; j++) {
				if (annotations[j].resolvedType != null && annotations[j].resolvedType.id == typeId) {
					return annotations[j];
				}
			}
		}
		return null;
	}
	// https://bugs.eclipse.org/bugs/show_bug.cgi?id=372012
	public void missingNonNullByDefaultAnnotation(TypeDeclaration type) {
		int severity;
		CompilationUnitDeclaration compUnitDecl = type.getCompilationUnitDeclaration();
		String[] arguments;
		if (compUnitDecl.currentPackage == null) {
			severity = computeSeverity(IProblem.MissingNonNullByDefaultAnnotationOnType);
			if (severity == ProblemSeverities.Ignore) return;
			// Default package
			TypeBinding binding = type.binding;
			this.handle(
					IProblem.MissingNonNullByDefaultAnnotationOnType,
					new String[] {new String(binding.readableName()), },
					new String[] {new String(binding.shortReadableName()),},
					severity,
					type.sourceStart,
					type.sourceEnd);
		} else {
			severity = computeSeverity(IProblem.MissingNonNullByDefaultAnnotationOnPackage);
			if (severity == ProblemSeverities.Ignore) return;
			arguments = new String[] {CharOperation.toString(compUnitDecl.currentPackage.tokens)};
			this.handle(
				IProblem.MissingNonNullByDefaultAnnotationOnPackage,
				arguments,
				arguments,
				severity,
				compUnitDecl.currentPackage.sourceStart,
				compUnitDecl.currentPackage.sourceEnd);
		}
	}
	
	public void illegalModifiersForElidedType(Argument argument) {
		String[] arg = new String[] {new String(argument.name)};
		this.handle(
				IProblem.IllegalModifiersForElidedType,
				arg,
				arg,
				argument.declarationSourceStart,
				argument.declarationSourceEnd);
	}
	
	public void illegalModifiers(int modifierSourceStart, int modifiersSourceEnd) {
		this.handle(
				IProblem.IllegalModifiers,
				NoArgument,
				NoArgument,
				modifierSourceStart,
				modifiersSourceEnd);
	}
	
	public void arrayReferencePotentialNullReference(ArrayReference arrayReference) {
		// TODO(stephan): merge with other expressions
		this.handle(IProblem.ArrayReferencePotentialNullReference, NoArgument, NoArgument, arrayReference.sourceStart, arrayReference.sourceEnd);
		
	}
	public void nullityMismatchingTypeAnnotation(Expression expression, TypeBinding providedType, TypeBinding requiredType, NullAnnotationMatching status) 
	{
		if (providedType.id == TypeIds.T_null) {
			nullityMismatchIsNull(expression, requiredType);
			return;
		}
		String[] arguments ;
		String[] shortArguments;
			
		int problemId = 0;
		String superHint = null;
		String superHintShort = null;
		if (status.superTypeHint != null) {
			problemId = (status.isUnchecked()
				? IProblem.NullityUncheckedTypeAnnotationDetailSuperHint
				: IProblem.NullityMismatchingTypeAnnotationSuperHint);
			superHint = status.superTypeHintName(this.options, false);
			superHintShort = status.superTypeHintName(this.options, true);
		} else {
			problemId = (status.isUnchecked()
				? IProblem.NullityUncheckedTypeAnnotationDetail
				: (requiredType.isTypeVariable() && !requiredType.hasNullTypeAnnotations())
					? IProblem.NullityMismatchAgainstFreeTypeVariable
					: IProblem.NullityMismatchingTypeAnnotation);
			if (problemId == IProblem.NullityMismatchAgainstFreeTypeVariable) {
				arguments      = new String[] { null, null, new String(requiredType.sourceName()) }; // don't show bounds here
				shortArguments = new String[] { null, null, new String(requiredType.sourceName()) };
			} else {
				arguments      = new String[2];
				shortArguments = new String[2];
			}
		}
		String requiredName;
		String requiredNameShort;
		if (problemId == IProblem.NullityMismatchAgainstFreeTypeVariable) {
			requiredName		= new String(requiredType.sourceName()); // don't show bounds here
			requiredNameShort 	= new String(requiredType.sourceName()); // don't show bounds here
		} else {
			requiredName 		= new String(requiredType.nullAnnotatedReadableName(this.options, false));
			requiredNameShort 	= new String(requiredType.nullAnnotatedReadableName(this.options, true));
		}
		String providedName		 = String.valueOf(providedType.nullAnnotatedReadableName(this.options, false));
		String providedNameShort = String.valueOf(providedType.nullAnnotatedReadableName(this.options, true));
		// assemble arguments:
		if (superHint != null) {
			arguments 		= new String[] { requiredName, providedName, superHint };
			shortArguments 	= new String[] { requiredNameShort, providedNameShort, superHintShort };
		} else {
			arguments 		= new String[] { requiredName, providedName };
			shortArguments 	= new String[] { requiredNameShort, providedNameShort };
		}
		this.handle(problemId, arguments, shortArguments, expression.sourceStart, expression.sourceEnd);
	}
	
	public void nullityMismatchTypeArgument(TypeBinding typeVariable, TypeBinding typeArgument, ASTNode location) {
		String[] arguments = {
			String.valueOf(typeVariable.nullAnnotatedReadableName(this.options, false)),
			String.valueOf(typeArgument.nullAnnotatedReadableName(this.options, false))
		};
		String[] shortArguments = {
			String.valueOf(typeVariable.nullAnnotatedReadableName(this.options, true)),
			String.valueOf(typeArgument.nullAnnotatedReadableName(this.options, true))
		};
		this.handle(
				IProblem.NullityMismatchTypeArgument, 
				arguments, 
				shortArguments, 
				location.sourceStart, 
				location.sourceEnd);
	}
	
	public void dereferencingNullableExpression(Expression expression) {
		if (expression instanceof MessageSend) {
			MessageSend send = (MessageSend) expression;
			messageSendPotentialNullReference(send.binding, send);
			return;
		}
		char[][] nullableName = this.options.nullableAnnotationName;
		char[] nullableShort = nullableName[nullableName.length-1];
		String[] arguments = { String.valueOf(nullableShort) };
		// TODO(stephan): more sophisticated handling for various kinds of expressions
		int start = nodeSourceStart(expression);
		int end = nodeSourceEnd(expression);
		this.handle(IProblem.DereferencingNullableExpression, arguments, arguments, start, end);
	}
	public void dereferencingNullableExpression(long positions, LookupEnvironment env) {
		char[][] nullableName = env.getNullableAnnotationName();
		char[] nullableShort = nullableName[nullableName.length-1];
		String[] arguments = { String.valueOf(nullableShort) };
		this.handle(IProblem.DereferencingNullableExpression, arguments, arguments, (int)(positions>>>32), (int)(positions&0xFFFF));
	}
	public void onlyReferenceTypesInIntersectionCast(TypeReference typeReference) {
		this.handle(
				IProblem.IllegalBasetypeInIntersectionCast,
				NoArgument,
				NoArgument,
				typeReference.sourceStart,
				typeReference.sourceEnd);
	}
	public void illegalArrayTypeInIntersectionCast(TypeReference typeReference) {
		this.handle(
				IProblem.IllegalArrayTypeInIntersectionCast,
				NoArgument,
				NoArgument,
				typeReference.sourceStart,
				typeReference.sourceEnd);
	}
	public void intersectionCastNotBelow18(TypeReference[] typeReferences) {
		int length = typeReferences.length;
		this.handle(
				IProblem.IntersectionCastNotBelow18,
				NoArgument,
				NoArgument,
				typeReferences[0].sourceStart,
				typeReferences[length -1].sourceEnd);
	}
	
	public void duplicateBoundInIntersectionCast(TypeReference typeReference) {
		this.handle(
				IProblem.DuplicateBoundInIntersectionCast,
				NoArgument,
				NoArgument,
				typeReference.sourceStart,
				typeReference.sourceEnd);
	}
	
	public void multipleFunctionalInterfaces(FunctionalExpression functionalExpression) {
		this.handle(
				IProblem.MultipleFunctionalInterfaces,
				NoArgument,
				NoArgument,
				functionalExpression.sourceStart,
				functionalExpression.diagnosticsSourceEnd());
	}
	public void lambdaRedeclaresArgument(Argument argument) {
		String[] arguments = new String[] {new String(argument.name)};
		this.handle(
			IProblem.LambdaRedeclaresArgument,
			arguments,
			arguments,
			argument.sourceStart,
			argument.sourceEnd);
	}
	public void lambdaRedeclaresLocal(LocalDeclaration local) {
		String[] arguments = new String[] {new String(local.name)};
		this.handle(
			IProblem.LambdaRedeclaresLocal,
			arguments,
			arguments,
			local.sourceStart,
			local.sourceEnd);
	}
	
	public void descriptorHasInvisibleType(FunctionalExpression expression, ReferenceBinding referenceBinding) {
		this.handle(
			IProblem.LambdaDescriptorMentionsUnmentionable,
			new String[] { new String(referenceBinding.readableName()) },
			new String[] { new String(referenceBinding.shortReadableName()) },
			expression.sourceStart,
			expression.diagnosticsSourceEnd());
	}
	
	public void methodReferenceSwingsBothWays(ReferenceExpression expression, MethodBinding instanceMethod, MethodBinding nonInstanceMethod) {
		char [] selector = instanceMethod.selector;
		TypeBinding receiverType = instanceMethod.declaringClass;
		StringBuffer buffer1 = new StringBuffer();
		StringBuffer shortBuffer1 = new StringBuffer();
		TypeBinding [] parameters = instanceMethod.parameters;
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0){
				buffer1.append(", "); //$NON-NLS-1$
				shortBuffer1.append(", "); //$NON-NLS-1$
			}
			buffer1.append(new String(parameters[i].readableName()));
			shortBuffer1.append(new String(parameters[i].shortReadableName()));
		}
		StringBuffer buffer2 = new StringBuffer();
		StringBuffer shortBuffer2 = new StringBuffer();
		parameters = nonInstanceMethod.parameters;
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0){
				buffer2.append(", "); //$NON-NLS-1$
				shortBuffer2.append(", "); //$NON-NLS-1$
			}
			buffer2.append(new String(parameters[i].readableName()));
			shortBuffer2.append(new String(parameters[i].shortReadableName()));
		}
	
		int id = IProblem.MethodReferenceSwingsBothWays;
		this.handle(
			id,
			new String[] { new String(receiverType.readableName()), new String(selector), buffer1.toString(), new String(selector), buffer2.toString() },
			new String[] { new String(receiverType.shortReadableName()), new String(selector), shortBuffer1.toString(), new String(selector), shortBuffer2.toString() },
			expression.sourceStart,
			expression.sourceEnd);
	}
	
	public void methodMustBeAccessedStatically(ReferenceExpression expression, MethodBinding nonInstanceMethod) {
		TypeBinding receiverType = nonInstanceMethod.declaringClass;
		char [] selector = nonInstanceMethod.selector;
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		TypeBinding [] parameters = nonInstanceMethod.parameters;
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0){
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(parameters[i].readableName()));
			shortBuffer.append(new String(parameters[i].shortReadableName()));
		}
		int id = IProblem.StaticMethodShouldBeAccessedStatically;
		this.handle(
			id,
			new String[] { new String(receiverType.readableName()), new String(selector), buffer.toString() },
			new String[] { new String(receiverType.shortReadableName()), new String(selector), shortBuffer.toString() },
			expression.sourceStart,
			expression.sourceEnd);
	}
	
	public void methodMustBeAccessedWithInstance(ReferenceExpression expression, MethodBinding instanceMethod) {
		TypeBinding receiverType = instanceMethod.declaringClass;
		char [] selector = instanceMethod.selector;
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		TypeBinding [] parameters = instanceMethod.parameters;
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(parameters[i].readableName()));
			shortBuffer.append(new String(parameters[i].shortReadableName()));
		}
		int id = IProblem.StaticMethodRequested;
		this.handle(
			id,
			new String[] { new String(receiverType.readableName()), new String(selector), buffer.toString() },
			new String[] { new String(receiverType.shortReadableName()), new String(selector), shortBuffer.toString() },
			expression.sourceStart,
			expression.sourceEnd);
	}
	
	public void invalidArrayConstructorReference(ReferenceExpression expression, TypeBinding lhsType, TypeBinding[] parameters) {
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(parameters[i].readableName()));
			shortBuffer.append(new String(parameters[i].shortReadableName()));
		}
		int id = IProblem.InvalidArrayConstructorReference;
		this.handle(
			id,
			new String[] { new String(lhsType.readableName()), buffer.toString() },
			new String[] { new String(lhsType.shortReadableName()), shortBuffer.toString() },
			expression.sourceStart,
			expression.sourceEnd);
	}
	
	public void constructedArrayIncompatible(ReferenceExpression expression, TypeBinding receiverType, TypeBinding returnType) {
		this.handle(
				IProblem.ConstructedArrayIncompatible,
				new String[] { new String(receiverType.readableName()), new String(returnType.readableName()) },
				new String[] { new String(receiverType.shortReadableName()), new String(returnType.shortReadableName()) },
				expression.sourceStart,
				expression.sourceEnd);
	}
	
	public void danglingReference(ReferenceExpression expression, TypeBinding receiverType, char[] selector, TypeBinding[] descriptorParameters) {
		StringBuffer buffer = new StringBuffer();
		StringBuffer shortBuffer = new StringBuffer();
		TypeBinding [] parameters = descriptorParameters;
		for (int i = 0, length = parameters.length; i < length; i++) {
			if (i != 0) {
				buffer.append(", "); //$NON-NLS-1$
				shortBuffer.append(", "); //$NON-NLS-1$
			}
			buffer.append(new String(parameters[i].readableName()));
			shortBuffer.append(new String(parameters[i].shortReadableName()));
		}
		
		int id = IProblem.DanglingReference;
		this.handle(
			id,
			new String[] { new String(receiverType.readableName()), new String(selector), buffer.toString() },
			new String[] { new String(receiverType.shortReadableName()), new String(selector), shortBuffer.toString() },
			expression.sourceStart,
			expression.sourceEnd);
	}
	public void unhandledException(TypeBinding exceptionType, ReferenceExpression location) {
		this.handle(IProblem.UnhandledException,
			new String[] {new String(exceptionType.readableName())},
			new String[] {new String(exceptionType.shortReadableName())},
			location.sourceStart,
			location.sourceEnd);
	}
	
	public void incompatibleReturnType(ReferenceExpression expression, MethodBinding method, TypeBinding returnType) {
		if (method.isConstructor()) {
			this.handle(IProblem.ConstructionTypeMismatch,
					new String[] { new String(method.declaringClass.readableName()), new String(returnType.readableName())},
					new String[] { new String(method.declaringClass.shortReadableName()), new String(returnType.shortReadableName())},
					expression.sourceStart,
					expression.sourceEnd);
			
		} else {
			StringBuffer buffer = new StringBuffer();
			StringBuffer shortBuffer = new StringBuffer();
			TypeBinding [] parameters = method.parameters;
			for (int i = 0, length = parameters.length; i < length; i++) {
				if (i != 0) {
					buffer.append(", "); //$NON-NLS-1$
					shortBuffer.append(", "); //$NON-NLS-1$
				}
				buffer.append(new String(parameters[i].readableName()));
				shortBuffer.append(new String(parameters[i].shortReadableName()));
			}
			String selector = new String(method.selector);
			this.handle(IProblem.IncompatibleMethodReference,
					new String[] { selector, buffer.toString(), new String(method.declaringClass.readableName()), new String(method.returnType.readableName()), new String(returnType.readableName())},
					new String[] { selector, shortBuffer.toString(), new String(method.declaringClass.shortReadableName()), new String(method.returnType.shortReadableName()), new String(returnType.shortReadableName())},
					expression.sourceStart,
					expression.sourceEnd);
		}
	}
	
	public void illegalSuperAccess(TypeBinding superType, TypeBinding directSuperType, ASTNode location) {
		if (directSuperType.problemId() != ProblemReasons.AttemptToBypassDirectSuper)
			needImplementation(location);
		handle(IProblem.SuperAccessCannotBypassDirectSuper, 
				new String[] { String.valueOf(superType.readableName()), String.valueOf(directSuperType.readableName()) },
				new String[] { String.valueOf(superType.shortReadableName()), String.valueOf(directSuperType.shortReadableName()) },
				location.sourceStart,
				location.sourceEnd);
	}
	public void illegalSuperCallBypassingOverride(InvocationSite location, MethodBinding targetMethod, ReferenceBinding overrider) {
		this.handle(IProblem.SuperCallCannotBypassOverride,
				new String[] { 	String.valueOf(targetMethod.readableName()),
								String.valueOf(targetMethod.declaringClass.readableName()),
								String.valueOf(overrider.readableName()) },
				new String[] { 	String.valueOf(targetMethod.shortReadableName()),
								String.valueOf(targetMethod.declaringClass.shortReadableName()),
								String.valueOf(overrider.shortReadableName()) },
				location.sourceStart(),
				location.sourceEnd());
	}
	public void disallowedTargetForContainerAnnotation(Annotation annotation, TypeBinding containerAnnotationType) {
		this.handle(
			IProblem.DisallowedTargetForContainerAnnotationType,
			new String[] {new String(annotation.resolvedType.readableName()), new String(containerAnnotationType.readableName())},
			new String[] {new String(annotation.resolvedType.shortReadableName()), new String(containerAnnotationType.shortReadableName())},
			annotation.sourceStart,
			annotation.sourceEnd);
	}
	public void genericInferenceError(String message, InvocationSite invocationSite) {
		genericInferenceProblem(message, invocationSite, ProblemSeverities.Error);
	}
	public void genericInferenceProblem(String message, InvocationSite invocationSite, int severity) {
		String[] args = new String[]{message};
		int start = 0, end = 0;
		if (invocationSite != null) {
			start = invocationSite.sourceStart();
			end = invocationSite.sourceEnd();
		}
		this.handle(IProblem.GenericInferenceError, args, args, severity|ProblemSeverities.InternalError, start, end);	
	}
	public void uninternedIdentityComparison(EqualExpression expr, TypeBinding lhs, TypeBinding rhs, CompilationUnitDeclaration unit) {
		
		char [] lhsName = lhs.sourceName();
		char [] rhsName = rhs.sourceName();
		
		if (CharOperation.equals(lhsName, "VoidTypeBinding".toCharArray())  //$NON-NLS-1$
				|| CharOperation.equals(lhsName, "NullTypeBinding".toCharArray())  //$NON-NLS-1$
				|| CharOperation.equals(lhsName, "ProblemReferenceBinding".toCharArray())) //$NON-NLS-1$
			return;
		
		if (CharOperation.equals(rhsName, "VoidTypeBinding".toCharArray())  //$NON-NLS-1$
				|| CharOperation.equals(rhsName, "NullTypeBinding".toCharArray())  //$NON-NLS-1$
				|| CharOperation.equals(rhsName, "ProblemReferenceBinding".toCharArray())) //$NON-NLS-1$
			return;
		
		boolean[] validIdentityComparisonLines = unit.validIdentityComparisonLines;
		if (validIdentityComparisonLines != null) {
			int problemStartPosition = expr.left.sourceStart;
			int[] lineEnds;
			int lineNumber = problemStartPosition >= 0
					? Util.getLineNumber(problemStartPosition, lineEnds = unit.compilationResult().getLineSeparatorPositions(), 0, lineEnds.length-1)
							: 0;
			if (lineNumber <= validIdentityComparisonLines.length && validIdentityComparisonLines[lineNumber - 1])
				return;
		}
		
		this.handle(
				IProblem.UninternedIdentityComparison,
				new String[] {
						new String(lhs.readableName()),
						new String(rhs.readableName())
				},
				new String[] {
						new String(lhs.shortReadableName()),
						new String(rhs.shortReadableName())
				},
				expr.sourceStart,
				expr.sourceEnd);
	}
	
	public void lambdaShapeComputationError(LambdaExpression expression) {
		this.handle(
				IProblem.LambdaShapeComputationError,
				NoArgument,
				NoArgument,
				expression.sourceStart,
				expression.diagnosticsSourceEnd());
	}
}
